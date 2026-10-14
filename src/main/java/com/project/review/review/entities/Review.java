package com.project.review.review.entities;

import com.project.review.member.entities.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

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

    @Min(0)
    @Max(5)
    @Column(nullable = false)
    private double rating;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @Builder
    public Review(String title, String content, double rating, Member author){
        this.title = title;
        this.content = content;
        this.rating = rating;
        this.author = author;
    }
}
