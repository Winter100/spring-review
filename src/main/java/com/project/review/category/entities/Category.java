package com.project.review.category.entities;

import com.project.review.review.entities.Review;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Todo
 * 카테고리 테이블
 * - 아이디
 * - 카테고리명
 * - 리뷰는 1개의 카테고리를 갖게됨
 */

@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_categories_category", columnNames = "category")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String category;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "category")
    private List<Review> reviews = new ArrayList<>();

    public Category(String category) {
        changeCategory(category);
    }

    public void changeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("카테고리는 필수입니다.");
        }
        if (category.length() > 255) {
            throw new IllegalArgumentException("카테고리는 255자 이하여야 합니다.");
        }
        this.category = category;
    }
}
