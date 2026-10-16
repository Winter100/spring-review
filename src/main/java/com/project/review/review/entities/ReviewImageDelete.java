package com.project.review.review.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 리뷰에서 제거된 이미지의 스토리지 삭제 작업을 보관합니다.
 * 리뷰가 삭제된 뒤에도 작업이 남도록 리뷰 엔티티와 연관관계를 맺지 않습니다.
 */

@Entity
@Table(name = "reviews_images_delete", indexes = {
        @Index(name = "idx_image_delete_status_next_attempt", columnList = "status, next_attempt_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImageDelete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "이미지 키는 필수입니다.")
    @Size(max = 1024, message = "이미지 키는 최대 1024자입니다.")
    @Column(nullable = false, length = 1024, updatable = false)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageDeleteStatus status = ImageDeleteStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant deletedAt;

    // 삭제 실패 횟수
    @Column(nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    public ReviewImageDelete(String imageKey) {
        this.imageKey = imageKey;
        this.nextAttemptAt = Instant.now();
    }

    // 스토리지 삭제 성공을 확인한 뒤 호출합니다.
    public void markDeleted() {
        this.status = ImageDeleteStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    // 실패 시 PENDING 상태를 유지하고 다음 시도 시각을 기록합니다.
    public void scheduleRetry(Instant nextAttemptAt) {
        this.retryCount++;
        this.nextAttemptAt = nextAttemptAt;
    }
}
