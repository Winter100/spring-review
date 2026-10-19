package com.project.review.review;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewResponse;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.service.ReviewService;
import com.project.review.storage.entities.ImageUploadStatus;
import com.project.review.storage.entities.UploadStatus;
import com.project.review.storage.repository.ImageUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:upload-transactions;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15",
        "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com",
        "storage.region=auto",
        "storage.bucket=test-bucket",
        "storage.access-key=test-access-key",
        "storage.secret-key=test-secret-key",
        "storage.public-base-url=https://images.example.com"
})
class ReviewUploadTransactionTests {
    @Autowired private ReviewService reviewService;
    @Autowired private ReviewRepository reviews;
    @Autowired private ImageUploadRepository uploads;
    @Autowired private MemberRepository members;
    @Autowired private CategoryRepository categories;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private S3Client s3Client;

    private Member member;
    private Long categoryId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        uploads.deleteAll();
        members.deleteAll();
        categories.deleteAll();
        member = members.save(Member.builder().email("upload@example.com")
                .nickname("upload-user").password("unused").build());
        categoryId = categories.save(new Category("업로드 테스트")).getId();
        jwt = Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(member.getId().toString()).build();
    }

    @Test
    void createsImageLessReviewWithoutR2() {
        ReviewResponse result = reviewService.create(request("후기", null, null), jwt);
        assertThat(result.mainImageUrl()).isNull();
        assertThat(result.subImageUrls()).isEmpty();
        assertThat(reviews.count()).isEqualTo(1);
        verifyNoInteractions(s3Client);
    }

    @Test
    void checksR2OutsideTransactionAndCommitsImagesInOriginalOrder() {
        addUpload("reviews/z-main");
        addUpload("reviews/a-sub");
        mockHead(() -> {});

        ReviewResponse result = reviewService.create(
                request("후기", "reviews/z-main", List.of("reviews/a-sub")), jwt);

        assertThat(result.mainImageUrl()).endsWith("/reviews/z-main");
        assertThat(result.subImageUrls()).containsExactly("https://images.example.com/reviews/a-sub");
        assertThat(uploads.findAll()).allMatch(upload -> upload.getStatus() == UploadStatus.ATTACHED);
        assertThat(reviews.count()).isEqualTo(1);
    }

    @Test
    void rejectsUnissuedKeyBeforeR2() {
        assertThatThrownBy(() -> reviewService.create(request("후기", "reviews/missing", null), jwt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reviews.count()).isZero();
        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsSizeMismatchWithoutChangingPending() {
        addUpload("reviews/image");
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenReturn(HeadObjectResponse.builder().contentLength(101L).contentType("image/png").build());

        assertThatThrownBy(() -> reviewService.create(request("후기", "reviews/image", null), jwt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(status("reviews/image")).isEqualTo(UploadStatus.PENDING);
        assertThat(reviews.count()).isZero();
    }

    @Test
    void rejectsTypeMismatchWithoutChangingPending() {
        addUpload("reviews/image");
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenReturn(HeadObjectResponse.builder().contentLength(100L).contentType("image/jpeg").build());

        assertThatThrownBy(() -> reviewService.create(request("후기", "reviews/image", null), jwt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(status("reviews/image")).isEqualTo(UploadStatus.PENDING);
        assertThat(reviews.count()).isZero();
    }

    @Test
    void rejectsUploadExpiredDuringHead() {
        addUpload("reviews/image");
        mockHead(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            // 외부 조회 중 연결 기한이 지난 상황을 재현합니다.
            var upload = uploads.findAllByImageKeyIn(List.of("reviews/image")).getFirst();
            uploads.delete(upload);
            uploads.flush();
            uploads.save(new ImageUploadStatus(member, "reviews/image", "image/png", 100L,
                    UploadStatus.PENDING, Instant.now().minusSeconds(300), Instant.now().minusSeconds(1)));
        }));

        assertThatThrownBy(() -> reviewService.create(request("후기", "reviews/image", null), jwt))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.IMAGE_UPLOAD_CONFLICT));
        assertThat(status("reviews/image")).isEqualTo(UploadStatus.PENDING);
        assertThat(reviews.count()).isZero();
    }

    @Test
    void rollsBackAttachmentWhenReviewPersistenceFails() {
        addUpload("reviews/image");
        mockHead(() -> {});

        assertThatThrownBy(() -> reviewService.create(request("x".repeat(31), "reviews/image", null), jwt))
                .isInstanceOf(RuntimeException.class);
        assertThat(status("reviews/image")).isEqualTo(UploadStatus.PENDING);
        assertThat(reviews.count()).isZero();
    }

    @Test
    void rollsBackEarlierAttachmentWhenAnotherKeyWasAttachedDuringHead() {
        addUpload("reviews/a");
        addUpload("reviews/z");
        mockHead(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                uploads.attachIfPending("reviews/z", member.getId(), Instant.now(),
                        UploadStatus.PENDING, UploadStatus.ATTACHED)));

        assertThatThrownBy(() -> reviewService.create(request("후기", "reviews/a", List.of("reviews/z")), jwt))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.IMAGE_UPLOAD_CONFLICT));
        assertThat(status("reviews/a")).isEqualTo(UploadStatus.PENDING);
        assertThat(status("reviews/z")).isEqualTo(UploadStatus.ATTACHED);
        assertThat(reviews.count()).isZero();
    }

    @Test
    void onlyOneConcurrentReviewCanAttachSameKey() throws Exception {
        addUpload("reviews/shared");
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    barrier.await(10, TimeUnit.SECONDS);
                    return objectInfo();
                });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(this::attemptSharedReview);
            var second = executor.submit(this::attemptSharedReview);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(reviews.count()).isEqualTo(1);
        assertThat(status("reviews/shared")).isEqualTo(UploadStatus.ATTACHED);
    }

    private boolean attemptSharedReview() {
        try {
            reviewService.create(request("후기", "reviews/shared", null), jwt);
            return true;
        } catch (BusinessException ex) {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.IMAGE_UPLOAD_CONFLICT);
            return false;
        }
    }

    private void mockHead(Runnable duringHead) {
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    Consumer<HeadObjectRequest.Builder> configure = invocation.getArgument(0);
                    HeadObjectRequest.Builder builder = HeadObjectRequest.builder();
                    configure.accept(builder);
                    assertThat(builder.build().bucket()).isEqualTo("test-bucket");
                    duringHead.run();
                    return objectInfo();
                });
    }

    private HeadObjectResponse objectInfo() {
        return HeadObjectResponse.builder().contentLength(100L).contentType("image/png").build();
    }

    private void addUpload(String key) {
        uploads.save(new ImageUploadStatus(member, key, "image/png", 100L, UploadStatus.PENDING,
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(3600)));
    }

    private UploadStatus status(String key) {
        return uploads.findAllByImageKeyIn(List.of(key)).getFirst().getStatus();
    }

    private ReviewRequest request(String title, String mainKey, List<String> subKeys) {
        return new ReviewRequest(title, "내용", new BigDecimal("4.5"), categoryId, mainKey, subKeys);
    }
}
