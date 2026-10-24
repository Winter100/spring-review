package com.project.review.review.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "review_daily_views", indexes = @Index(name = "idx_review_daily_views_date", columnList = "viewed_on"))
@IdClass(ReviewDailyView.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewDailyView {

    @Id
    @Column(name = "review_id")
    private Long reviewId;

    @Id
    @Column(name = "visitor_id")
    private UUID visitorId;

    @Id
    @Column(name = "viewed_on")
    private LocalDate viewedOn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Review review;

    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long reviewId;
        private UUID visitorId;
        private LocalDate viewedOn;
    }
}
