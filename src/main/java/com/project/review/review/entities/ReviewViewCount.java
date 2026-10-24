package com.project.review.review.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 조회수는 SQL로만 갱신하여 리뷰의 수정 버전과 분리합니다. */
@Entity
@Table(name = "review_view_counts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewViewCount {

    @Id
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Review review;
}
