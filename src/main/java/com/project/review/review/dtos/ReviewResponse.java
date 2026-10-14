package com.project.review.review.dtos;

import com.project.review.member.dtos.MemberSummaryResponse;
import com.project.review.review.entities.Review;

import java.time.Instant;

public record ReviewResponse(
        String title,
        String content,
        double rating,
        Instant createdAt,
        MemberSummaryResponse author
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getTitle(),
                review.getContent(),
                review.getRating(),
                review.getCreatedAt(),
                MemberSummaryResponse.from(review.getAuthor())
        );
    }
}
