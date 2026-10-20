package com.project.review.review.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

public record ReviewPageResponse(
        List<ReviewSummaryResponse> content,
        @Schema(description = "1부터 시작하는 현재 페이지", example = "1") int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static ReviewPageResponse from(Page<ReviewSummaryResponse> reviews) {
        return new ReviewPageResponse(
                reviews.getContent(),
                reviews.getNumber() + 1,
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }
}
