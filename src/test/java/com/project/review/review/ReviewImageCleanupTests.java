package com.project.review.review;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.ImageDeleteStatus;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.service.ReviewImageCleanup;
import com.project.review.review.service.ImageDeletionTime;
import com.project.review.storage.repository.ImageUploadRepository;
import com.project.review.storage.entities.ImageUploadStatus;
import com.project.review.storage.entities.UploadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:image-cleanup;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
    "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
    "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
    "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
    "storage.public-base-url=https://images.example.com"
})
class ReviewImageCleanupTests {
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
    @Autowired ReviewImageCleanup cleanup;
    @Autowired ReviewImageDeleteRepository deletions;
    @Autowired ReviewRepository reviews;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired ImageUploadRepository uploads;
    @Autowired ConfigurableApplicationContext context;
    @MockitoSpyBean TransactionTemplate transactions;
    @MockitoBean S3Client s3;

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        Clock fixedDeletionClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @BeforeEach
    void setUp() {
        deletions.deleteAll();
        reviews.deleteAll();
        uploads.deleteAll();
        members.deleteAll();
        categories.deleteAll();
    }

    @Test
    void deletesDueObjectsOutsideTransactionAndRecordsCompletion() {
        ReviewImageDelete due = enqueue("reviews/due", NOW);
        ReviewImageDelete future = enqueue("reviews/future", NOW.plusSeconds(1));
        mockDelete(DeleteObjectsResponse.builder().build());

        cleanup.deleteUnusedImages();

        assertThat(load(due).getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
        assertThat(load(due).getDeletedAt()).isEqualTo(NOW);
        assertThat(load(future).getStatus()).isEqualTo(ImageDeleteStatus.PENDING);
        verify(s3, times(1)).deleteObjects(anyDelete());
    }

    @Test
    void retriesOnlyFailedKeysAtNextMidnight() {
        ReviewImageDelete good = enqueue("reviews/good", NOW.minusSeconds(86400));
        ReviewImageDelete bad = enqueue("reviews/bad", NOW.minusSeconds(86400));
        mockDelete(DeleteObjectsResponse.builder().errors(S3Error.builder()
                .key("reviews/bad").code("AccessDenied").message("denied").build()).build());

        cleanup.deleteUnusedImages();
        cleanup.deleteUnusedImages();

        assertThat(load(good).getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
        assertRetry(load(bad));
        verify(s3, times(1)).deleteObjects(anyDelete());
    }

    @Test
    void wholeRequestFailureKeepsAllJobsPending() {
        ReviewImageDelete row = enqueue("reviews/failure", NOW);
        when(s3.deleteObjects(anyDelete())).thenThrow(new IllegalStateException("R2 unavailable"));
        cleanup.deleteUnusedImages();
        assertRetry(load(row));
    }

    @Test
    void readyEventProcessesJobsMissedWhileServerWasDown() {
        ReviewImageDelete overdue = enqueue("reviews/overdue", NOW.minusSeconds(86400));
        mockDelete(DeleteObjectsResponse.builder().build());
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
        assertThat(load(overdue).getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
    }

    @Test
    void retriesR2DeletionAfterCompletionRecordingFails() {
        ReviewImageDelete row = enqueue("reviews/db-failure", NOW);
        mockDelete(DeleteObjectsResponse.builder().build());
        doCallRealMethod().doThrow(new IllegalStateException("database unavailable")).doCallRealMethod()
                .when(transactions).executeWithoutResult(any());

        cleanup.deleteUnusedImages();
        assertThat(load(row).getStatus()).isEqualTo(ImageDeleteStatus.PENDING);
        assertThat(load(row).getDeletedAt()).isNull();

        cleanup.deleteUnusedImages();
        assertThat(load(row).getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
        verify(s3, times(2)).deleteObjects(anyDelete());
    }

    @Test
    void closesLegacyPendingUploadBeforeReferenceCheckAndR2Deletion() {
        Member member = members.save(Member.builder().email("legacy@example.com").nickname("legacy").password("unused").build());
        uploads.saveAndFlush(new ImageUploadStatus(member, "reviews/legacy", "image/png", 100L,
                UploadStatus.PENDING, NOW.plusSeconds(300), NOW.plusSeconds(3600)));
        enqueue("reviews/legacy", NOW);
        when(s3.deleteObjects(anyDelete())).thenAnswer(invocation -> {
            assertThat(uploads.findAllByImageKeyIn(List.of("reviews/legacy")).getFirst().getStatus())
                    .isEqualTo(UploadStatus.EXPIRED);
            int attached = transactions.execute(status -> uploads.attachIfPending("reviews/legacy", member.getId(), NOW,
                    UploadStatus.PENDING, UploadStatus.ATTACHED));
            assertThat(attached).isZero();
            return DeleteObjectsResponse.builder().build();
        });
        cleanup.deleteUnusedImages();
        assertThat(deletions.findAll().getFirst().getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
    }

    @Test
    void missingObjectIsSuccessfulAndDuplicateKeysAreSentOnce() {
        ReviewImageDelete first = enqueue("reviews/missing", NOW);
        ReviewImageDelete second = enqueue("reviews/missing", NOW);
        mockDelete(DeleteObjectsResponse.builder().errors(S3Error.builder()
                .key("reviews/missing").code("NoSuchKey").build()).build());
        cleanup.deleteUnusedImages();
        assertThat(load(first).getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
        assertThat(load(second).getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
    }

    @Test
    void retainsGalleryAndNestedBodyReferencesIncludingPublicUrls() {
        Member member = members.save(Member.builder().email("refs@example.com").nickname("refs").password("unused").build());
        Category category = categories.save(new Category("refs"));
        Review review = Review.builder().title("refs").product("product").author(member).category(category)
                .rating(BigDecimal.ONE).content(JsonMapper.builder().build().readTree("""
                    {"type":"doc","content":[{"type":"blockquote","content":[
                      {"type":"image","attrs":{"src":"reviews/body"}},
                      {"type":"image","attrs":{"src":"https://images.example.com/reviews/url"}},
                      {"type":"image","attrs":{"src":"https://external.example.com/reviews/external"}}
                    ]}]}
                    """)).build();
        review.addImage("reviews/gallery", 0);
        reviews.saveAndFlush(review);
        ReviewImageDelete gallery = enqueue("reviews/gallery", NOW);
        ReviewImageDelete body = enqueue("reviews/body", NOW);
        ReviewImageDelete url = enqueue("reviews/url", NOW);
        ReviewImageDelete external = enqueue("reviews/external", NOW);
        mockDelete(DeleteObjectsResponse.builder().build());

        cleanup.deleteUnusedImages();

        for (ReviewImageDelete row : List.of(gallery, body, url)) {
            assertThat(load(row).getStatus()).isEqualTo(ImageDeleteStatus.PENDING);
            assertThat(load(row).getNextAttemptAt()).isEqualTo(ImageDeletionTime.nextMidnight(NOW));
            assertThat(load(row).getRetryCount()).isZero();
        }
        assertThat(load(external).getStatus()).isEqualTo(ImageDeleteStatus.DELETED);
    }

    @Test
    void processesMoreThanOneThousandWithoutSkippingJobs() {
        deletions.saveAll(IntStream.range(0, 1001)
                .mapToObj(index -> new ReviewImageDelete("reviews/batch-" + index, NOW)).toList());
        List<Integer> sizes = new ArrayList<>();
        when(s3.deleteObjects(anyDelete())).thenAnswer(invocation -> {
            DeleteObjectsRequest request = request(invocation.getArgument(0));
            sizes.add(request.delete().objects().size());
            return DeleteObjectsResponse.builder().build();
        });

        cleanup.deleteUnusedImages();

        assertThat(sizes).containsExactly(1000, 1);
        assertThat(deletions.findAll()).allMatch(row -> row.getStatus() == ImageDeleteStatus.DELETED);
    }

    @Test
    void overlappingExecutionsDoNotSendDuplicateR2Requests() throws Exception {
        enqueue("reviews/concurrent", NOW);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(s3.deleteObjects(anyDelete())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return DeleteObjectsResponse.builder().build();
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(cleanup::deleteUnusedImages);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                cleanup.deleteUnusedImages();
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
        }
        verify(s3, times(1)).deleteObjects(anyDelete());
    }

    private void mockDelete(DeleteObjectsResponse response) {
        when(s3.deleteObjects(anyDelete())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            DeleteObjectsRequest request = request(invocation.getArgument(0));
            assertThat(request.bucket()).isEqualTo("test-bucket");
            assertThat(request.delete().objects()).hasSizeLessThanOrEqualTo(1000);
            assertThat(request.delete().objects()).extracting(object -> object.key()).doesNotHaveDuplicates();
            return response;
        });
    }

    private static Consumer<DeleteObjectsRequest.Builder> anyDelete() { return any(); }

    private DeleteObjectsRequest request(Consumer<DeleteObjectsRequest.Builder> configure) {
        var builder = DeleteObjectsRequest.builder();
        configure.accept(builder);
        return builder.build();
    }

    private ReviewImageDelete enqueue(String key, Instant at) {
        return deletions.saveAndFlush(new ReviewImageDelete(key, at));
    }

    private ReviewImageDelete load(ReviewImageDelete row) { return deletions.findById(row.getId()).orElseThrow(); }

    private void assertRetry(ReviewImageDelete row) {
        assertThat(row.getStatus()).isEqualTo(ImageDeleteStatus.PENDING);
        assertThat(row.getDeletedAt()).isNull();
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isEqualTo(ImageDeletionTime.nextMidnight(NOW));
    }
}
