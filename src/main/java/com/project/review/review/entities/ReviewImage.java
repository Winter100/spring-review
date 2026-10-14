package com.project.review.review.entities;

import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 리뷰 이미지의 R2 객체 키를 저장합니다. 순서 0은 대표 이미지, 1~4는 서브 이미지입니다. */
@Entity
@Table(
        name = "reviews_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_images_review_order", columnNames = {"review_id", "display_order"}
        ),
        check = @CheckConstraint(
                name = "ck_review_images_display_order", constraint = "display_order BETWEEN 0 AND 4"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이미지 행이 외래 키를 관리하며, 리뷰 정보는 접근할 때 조회합니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @NotBlank(message = "이미지 키는 필수입니다.")
    @Size(max = 1024, message = "이미지 키는 최대 1024자입니다.")
    @Column(name = "image_key", nullable = false, length = 1024)
    private String imageKey;

    // 범위 제약과 리뷰별 순서의 유니크 제약으로 최대 5개 슬롯만 허용합니다.
    @Min(0)
    @Max(4)
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ReviewImage(Review review, String imageKey, int displayOrder) {
        this.review = review;
        this.imageKey = imageKey;
        this.displayOrder = displayOrder;
    }
}
