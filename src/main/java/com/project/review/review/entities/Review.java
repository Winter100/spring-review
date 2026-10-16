package com.project.review.review.entities;

import com.project.review.category.entities.Category;
import com.project.review.member.entities.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 리뷰 엔티티
 * - 아이디
 * - 제목
 * - 내용
 * - 점수
 * - 작성자
 * - 이미지 최대 5장
 * -
 */

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Size(min = 1, max = 30)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Size(min = 1, max = 5000)
    private String content;

    @DecimalMin("0")
    @DecimalMax("5")
    @Column(nullable = false)
    private BigDecimal rating;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // 리뷰 저장 시 이미지도 함께 저장하며, 리뷰 삭제 시 연결된 이미지 행을 삭제합니다.
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ReviewImage> images = new ArrayList<>();

    public void addImage(String imageKey, int displayOrder) {
        images.add(new ReviewImage(this, imageKey, displayOrder));
    }

    @Builder
    public Review(String title, String content, BigDecimal rating, Member author, Category category) {
        this.title = title;
        this.content = content;
        this.rating = rating;
        this.author = author;
        this.category = category;
    }
}
