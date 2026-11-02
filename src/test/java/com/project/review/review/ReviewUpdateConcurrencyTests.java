package com.project.review.review;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.category.service.CategoryService;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.dtos.ReviewUpdateRequest;
import com.project.review.review.dtos.ReviewRequest;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImage;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.service.ReviewService;
import com.project.review.storage.entities.ImageUploadStatus;
import com.project.review.storage.entities.UploadStatus;
import com.project.review.storage.repository.ImageUploadRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:update-concurrency;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
    "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
    "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
    "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
    "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
class ReviewUpdateConcurrencyTests {
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewImageDeleteRepository deletions;
    @Autowired ImageUploadRepository uploads;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired MockMvc mvc;
    @MockitoSpyBean CategoryService categoryService;
    @MockitoBean S3Client s3;
    private Member member;
    private Long categoryId;
    private Long reviewId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        deletions.deleteAll();
        reviews.deleteAll();
        uploads.deleteAll();
        members.deleteAll();
        categories.deleteAll();
        member = members.save(Member.builder().email("update@example.com").nickname("update-user").password("unused").build());
        categoryId = categories.save(new Category("update-check")).getId();
        jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject(member.getId().toString()).build();
        Review review = Review.builder().title("original").product("Product").content(textDocument("original")).rating(BigDecimal.ONE)
                .author(member).category(categories.findById(categoryId).orElseThrow()).build();
        review.addImage("reviews/A", 0);
        review.addImage("reviews/B", 1);
        reviewId = reviews.saveAndFlush(review).getId();
        addUpload("reviews/A", UploadStatus.ATTACHED);
        addUpload("reviews/B", UploadStatus.ATTACHED);
    }

    @Test
    void replacementKeepsRetainedKeyAttachesNewKeyAndQueuesOnlyRemovedKey() {
        addUpload("reviews/C", UploadStatus.PENDING);
        mockHead(() -> {});
        service.update(reviewId, request("reviews/B", List.of("reviews/C")), jwt);
        Review result = reviews.findById(reviewId).orElseThrow();
        assertThat(result.getTitle()).isEqualTo("updated");
        assertThat(keys()).containsExactly("reviews/B", "reviews/C");
        assertThat(result.getImages()).extracting(ReviewImage::getDisplayOrder).containsExactly(0, 1);
        assertThat(queue()).containsExactly("reviews/A");
        assertThat(uploadStatus("reviews/C")).isEqualTo(UploadStatus.ATTACHED);
    }

    @Test
    void swapsMainAndSubWithoutQueueOrR2() {
        service.update(reviewId, request("reviews/B", List.of("reviews/A")), jwt);
        assertThat(keys()).containsExactly("reviews/B", "reviews/A");
        assertThat(queue()).isEmpty();
        verifyNoInteractions(s3);
    }

    @Test
    void retainsImageMovedFromGalleryToNestedBody() {
        service.update(reviewId, bodyRequest(body("reviews/A"), null), jwt);
        assertThat(keys()).isEmpty();
        assertThat(queue()).containsExactly("reviews/B");
        verifyNoInteractions(s3);
    }

    @Test
    void attachesBodyOnlyUploadAndQueuesItWhenRemoved() {
        addUpload("reviews/body-new", UploadStatus.PENDING);
        mockHead(() -> {});
        service.update(reviewId, bodyRequest(body("reviews/body-new"), "reviews/A"), jwt);
        assertThat(uploadStatus("reviews/body-new")).isEqualTo(UploadStatus.ATTACHED);
        assertThat(queue()).containsExactly("reviews/B");

        service.update(reviewId, bodyRequest(textDocument("no images"), "reviews/A"), jwt);
        assertThat(queue()).containsExactlyInAnyOrder("reviews/B", "reviews/body-new");
    }

    @Test
    void rejectsUnissuedBodyImageWithoutQueueOrReviewChanges() {
        assertThatThrownBy(() -> service.update(reviewId,
                bodyRequest(body("reviews/unissued"), "reviews/A"), jwt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(queue()).isEmpty();
        assertThat(keys()).containsExactly("reviews/A", "reviews/B");
    }

    @Test
    void deletionQueuesGalleryAndBodyKeysOnce() {
        service.update(reviewId, bodyRequest(body("reviews/A"), "reviews/A"), jwt);
        service.remove(reviewId, jwt);
        assertThat(queue()).containsExactlyInAnyOrder("reviews/A", "reviews/B");
        assertThat(reviews.existsById(reviewId)).isFalse();
    }

    @Test
    void deletionQueuesBodyOnlyImageAfterItWasAttached() {
        addUpload("reviews/body-only", UploadStatus.PENDING);
        mockHead(() -> {});
        service.update(reviewId, bodyRequest(body("reviews/body-only"), "reviews/A"), jwt);
        service.remove(reviewId, jwt);
        assertThat(queue()).containsExactlyInAnyOrder("reviews/A", "reviews/B", "reviews/body-only");
        assertThat(reviews.existsById(reviewId)).isFalse();
    }

    @Test
    void rollsBackNewBodyAttachmentAndQueueWhenUpdateFails() {
        addUpload("reviews/body-new", UploadStatus.PENDING);
        mockHead(() -> {});
        doThrow(new IllegalStateException("category failure")).when(categoryService).findById(categoryId);
        assertThatThrownBy(() -> service.update(reviewId,
                bodyRequest(body("reviews/body-new"), null), jwt)).isInstanceOf(IllegalStateException.class);
        assertThat(queue()).isEmpty();
        assertThat(keys()).containsExactly("reviews/A", "reviews/B");
        assertThat(uploadStatus("reviews/body-new")).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void createValidatesAndAttachesBodyOnlyImage() {
        addUpload("reviews/create-body", UploadStatus.PENDING);
        mockHead(() -> {});
        service.create(new ReviewRequest("body review", "Product", body("reviews/create-body"), BigDecimal.ONE,
                categoryId, null, List.of(), null, null, null, null), jwt);
        assertThat(uploadStatus("reviews/create-body")).isEqualTo(UploadStatus.ATTACHED);
        assertThat(reviews.count()).isEqualTo(2);
    }

    private ReviewUpdateRequest bodyRequest(JsonNode content, String main) {
        return new ReviewUpdateRequest("updated", content, BigDecimal.ONE, "Product", categoryId,
                main, List.of(), null, null, null, null);
    }

    private JsonNode body(String source) {
        return JsonMapper.builder().build().readTree("""
                {"type":"doc","content":[{"type":"blockquote","content":[
                  {"type":"image","attrs":{"src":"%s"}}
                ]}]}
                """.formatted(source));
    }

    @Test
    void removesAllImages() {
        service.update(reviewId, request(null, List.of()), jwt);
        assertThat(keys()).isEmpty();
        assertThat(queue()).containsExactlyInAnyOrder("reviews/A", "reviews/B");
        verifyNoInteractions(s3);
    }

    @Test
    void rollsBackAttachmentAndQueueWhenCategoryLookupFails() {
        addUpload("reviews/C", UploadStatus.PENDING);
        mockHead(() -> {});
        ReviewUpdateRequest request = new ReviewUpdateRequest("updated", textDocument("updated"), BigDecimal.ONE, "Product", Long.MAX_VALUE, "reviews/B", List.of("reviews/C"), null, null, null, null);
        assertThatThrownBy(() -> service.update(reviewId, request, jwt)).isInstanceOf(BusinessException.class);
        assertThat(keys()).containsExactly("reviews/A", "reviews/B");
        assertThat(queue()).isEmpty();
        assertThat(uploadStatus("reviews/C")).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void rollsBackFlushedChangesWhenFinalImageInsertFails() {
        addUpload("reviews/C", UploadStatus.PENDING);
        addUpload(" ", UploadStatus.PENDING);
        mockHead(() -> {});
        ReviewUpdateRequest invalid = new ReviewUpdateRequest("updated", textDocument("updated"), BigDecimal.ONE, "Product", categoryId, "reviews/C", List.of(" "), null, null, null, null);
        assertThatThrownBy(() -> service.update(reviewId, invalid, jwt)).isInstanceOf(RuntimeException.class);
        assertThat(keys()).containsExactly("reviews/A", "reviews/B");
        assertThat(reviews.findById(reviewId).orElseThrow().getTitle()).isEqualTo("original");
        assertThat(queue()).isEmpty();
        assertThat(uploadStatus("reviews/C")).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void rejectsStaleDiffWhenAnotherUpdateCommitsDuringR2Validation() {
        addUpload("reviews/C", UploadStatus.PENDING);
        addUpload("reviews/D", UploadStatus.PENDING);
        AtomicBoolean otherRequestRan = new AtomicBoolean();
        mockHead(() -> {
            if (otherRequestRan.compareAndSet(false, true)) {
                service.update(reviewId, request("reviews/A", List.of("reviews/C")), jwt);
            }
        });

        assertThatThrownBy(() -> service.update(reviewId, request("reviews/B", List.of("reviews/D")), jwt))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_UPDATE_CONFLICT));

        assertThat(otherRequestRan).isTrue();
        assertThat(keys()).containsExactly("reviews/A", "reviews/C");
        assertThat(queue()).containsExactly("reviews/B");
        assertThat(uploadStatus("reviews/C")).isEqualTo(UploadStatus.ATTACHED);
        assertThat(uploadStatus("reviews/D")).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void incrementsVersionWhenOnlyImageOrderChanges() {
        Long originalVersion = reviews.findById(reviewId).orElseThrow().getVersion();
        ReviewUpdateRequest request = new ReviewUpdateRequest("original", textDocument("original"), BigDecimal.ONE, "Product", categoryId, "reviews/B", List.of("reviews/A"), null, null, null, null);

        service.update(reviewId, request, jwt);

        assertThat(reviews.findById(reviewId).orElseThrow().getVersion()).isGreaterThan(originalVersion);
        assertThat(keys()).containsExactly("reviews/B", "reviews/A");
        assertThat(queue()).isEmpty();
        verifyNoInteractions(s3);
    }

    @Test
    void rejectsUpdateWhenReviewIsDeletedDuringR2Validation() {
        addUpload("reviews/C", UploadStatus.PENDING);
        mockHead(() -> service.remove(reviewId, jwt));

        assertThatThrownBy(() -> service.update(reviewId, request("reviews/C", List.of()), jwt))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_UPDATE_CONFLICT));

        assertThat(reviews.existsById(reviewId)).isFalse();
        assertThat(queue()).containsExactlyInAnyOrder("reviews/A", "reviews/B");
        assertThat(uploadStatus("reviews/C")).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void onlyOneUpdateCommitsWhenBothPassTheInitialVersionCheck() throws Exception {
        addUpload("reviews/C", UploadStatus.PENDING);
        addUpload("reviews/D", UploadStatus.PENDING);
        mockHead(() -> {});
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(categoryService).findById(categoryId);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptUpdate("reviews/C"));
            var second = executor.submit(() -> attemptUpdate("reviews/D"));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(queue()).containsExactly("reviews/A");
        assertThat(keys()).hasSize(2).contains("reviews/B").doesNotContain("reviews/A");
        assertThat(List.of(uploadStatus("reviews/C"), uploadStatus("reviews/D")))
                .containsExactlyInAnyOrder(UploadStatus.PENDING, UploadStatus.ATTACHED);
    }

    @Test
    void rollsBackUpdateWhenDeleteCommitsAfterTheVersionCheck() {
        addUpload("reviews/C", UploadStatus.PENDING);
        mockHead(() -> {});
        doAnswer(invocation -> {
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> service.remove(reviewId, jwt)).get(10, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(categoryService).findById(categoryId);

        assertThatThrownBy(() -> service.update(reviewId, request("reviews/B", List.of("reviews/C")), jwt))
                .isInstanceOfAny(OptimisticLockingFailureException.class, OptimisticLockException.class);

        assertThat(reviews.existsById(reviewId)).isFalse();
        assertThat(queue()).containsExactlyInAnyOrder("reviews/A", "reviews/B");
        assertThat(uploadStatus("reviews/C")).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void returnsConflictAndRollsBackWhenJpaDetectsAnOptimisticLockFailure() throws Exception {
        addUpload("reviews/C", UploadStatus.PENDING);
        mockHead(() -> {});
        doThrow(new ObjectOptimisticLockingFailureException(Review.class, reviewId))
                .when(categoryService).findById(categoryId);

        mvc.perform(put("/api/v1/reviews/{id}", reviewId)
                        .with(jwt().jwt(token -> token.subject(member.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"updated","content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"updated"}]}]},"rating":4.5,
                                 "categoryId":%d,"mainImageKey":"reviews/B","subImageKeys":["reviews/C"]}
                                """.formatted(categoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.REVIEW_UPDATE_CONFLICT.getMessage()));

        assertThat(keys()).containsExactly("reviews/A", "reviews/B");
        assertThat(queue()).isEmpty();
        assertThat(uploadStatus("reviews/C")).isEqualTo(UploadStatus.PENDING);
    }

    private boolean attemptUpdate(String newKey) {
        try {
            service.update(reviewId, request("reviews/B", List.of(newKey)), jwt);
            return true;
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            return false;
        } catch (BusinessException ex) {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_UPDATE_CONFLICT);
            return false;
        }
    }

    private ReviewUpdateRequest request(String main, List<String> sub) {
        return new ReviewUpdateRequest("updated", textDocument("updated"), new BigDecimal("4.5"), "Product", categoryId, main, sub, null, null, null, null);
    }

    private void addUpload(String key, UploadStatus status) {
        uploads.save(new ImageUploadStatus(member, key, "image/png", 100L, status,
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(3600)));
    }

    private List<String> keys() {
        return reviews.findById(reviewId).orElseThrow().getImages().stream()
                .map(ReviewImage::getImageKey)
                .toList();
    }

    private List<String> queue() {
        return deletions.findAll().stream().map(deletion -> deletion.getImageKey()).toList();
    }

    private UploadStatus uploadStatus(String key) {
        return uploads.findAllByImageKeyIn(List.of(key)).getFirst().getStatus();
    }

    private void mockHead(Runnable action) {
        when(s3.headObject(org.mockito.ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            action.run();
            return HeadObjectResponse.builder().contentLength(100L).contentType("image/png").build();
        });
    }
}
