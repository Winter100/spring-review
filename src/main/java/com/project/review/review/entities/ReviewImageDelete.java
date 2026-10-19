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

import java.time.Duration;
import java.time.Instant;

/**
 * 리뷰에서 제거된 이미지의 스토리지 삭제 작업을 보관합니다.
 * 리뷰가 삭제된 뒤에도 작업이 남도록 리뷰 엔티티와 연관관계를 맺지 않습니다.
 * Storage-ImageUploaStatus는 이미지 업로드 요청 시 발생한 이미지를 관리
 * Reviews-ReviewImageDelete는 리뷰의 수정, 삭제로 인해 발생한 이미지를 관리
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
    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;

    // 삭제 완료 시각
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 삭제 실패 횟수
    @Column(nullable = false)
    private int retryCount;

    // 이 시각 이후부터 삭제를 시도할 수 있으며, 실제 완료 시각은 deletedAt에 기록합니다.
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    public ReviewImageDelete(String imageKey) {
        this(imageKey, defaultNextAttemptAt());
    }

    public ReviewImageDelete(String imageKey, Instant nextAttemptAt) {
        this.imageKey = imageKey;
        this.nextAttemptAt = nextAttemptAt;
    }

    // 스토리지 삭제 성공을 확인한 뒤 호출합니다.
    public void markDeleted() {
        this.status = ImageDeleteStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    // 실패 시 PENDING 상태를 유지하고 다음 시도 시각을 기록합니다.
    public void scheduleRetry() {
        scheduleRetry(defaultNextAttemptAt());
    }
    public void scheduleRetry(Instant nextAttemptAt) {
        this.retryCount++;
        this.nextAttemptAt = nextAttemptAt;
    }

    private static Instant defaultNextAttemptAt(){
        return Instant.now().plus(Duration.ofHours(24));
    }
}
