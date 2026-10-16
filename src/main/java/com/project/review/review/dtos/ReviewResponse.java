package com.project.review.review.dtos;

import com.project.review.category.dtos.CategoryResponse;
import com.project.review.member.dtos.MemberSummaryResponse;
import com.project.review.review.entities.Review;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "리뷰 정보와 공개 이미지 URL")
public record ReviewResponse(
        Long id,
        String title,
        CategoryResponse category,
        String content,
        BigDecimal rating,
        Instant createdAt,
        String mainImageUrl,
        List<String> subImageUrls,
        MemberSummaryResponse author
) {

    // 공개 URL 생성은 서비스가 담당하고, DTO는 응답 필드 변환만 수행합니다.
    public static ReviewResponse from(Review review, String mainImageUrl, List<String> subImageUrls) {
        return new ReviewResponse(
                review.getId(),
                review.getTitle(),
                CategoryResponse.from(review.getCategory()),
                review.getContent(),
                review.getRating(),
                review.getCreatedAt(),
                mainImageUrl,
                subImageUrls,
                MemberSummaryResponse.from(review.getAuthor())
        );
    }
}
