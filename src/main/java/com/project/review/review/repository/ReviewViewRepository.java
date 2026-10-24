package com.project.review.review.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewViewRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public boolean insertDailyView(Long reviewId, UUID visitorId, LocalDate date) {
        return jdbc.update("""
                INSERT INTO review_daily_views (review_id, visitor_id, viewed_on)
                VALUES (:reviewId, :visitorId, :date)
                ON CONFLICT (review_id, visitor_id, viewed_on) DO NOTHING
                """, Map.of("reviewId", reviewId, "visitorId", visitorId, "date", Date.valueOf(date))) == 1;
    }

    public void increment(Long reviewId) {
        jdbc.update("""
                INSERT INTO review_view_counts (review_id, view_count) VALUES (:reviewId, 1)
                ON CONFLICT (review_id)
                DO UPDATE SET view_count = review_view_counts.view_count + 1
                """, Map.of("reviewId", reviewId));
    }

    public long findCount(Long reviewId) {
        return findCounts(List.of(reviewId)).getOrDefault(reviewId, 0L);
    }

    public Map<Long, Long> findCounts(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        jdbc.query("SELECT review_id, view_count FROM review_view_counts WHERE review_id IN (:ids)",
                Map.of("ids", reviewIds),
                row -> {
                    counts.put(row.getLong("review_id"), row.getLong("view_count"));
                });
        return counts;
    }

    public void deleteBefore(LocalDate cutoff) {
        jdbc.update("DELETE FROM review_daily_views WHERE viewed_on < :cutoff",
                Map.of("cutoff", Date.valueOf(cutoff)));
    }
}
