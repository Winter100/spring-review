package com.project.review.review.dtos;

import jakarta.validation.constraints.*;

/**
 * 리뷰 생성 시 필요 한 것
 * 제목
 * 내용
 * 이미지 (옵션) 최대 5장
 * 평점
 * - 카테고리
 *
 */
public record ReviewRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(min = 1, max = 30, message = "제목은 1~30자 사이입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(min = 1, max = 5000, message = "내용은 1~5000자 사이입니다.")
        String content,

        @NotNull(message = "평점은 필수입니다.")
        @Min(value = 0, message = "평점은 0 이상이어야 합니다.")
        @Max(value = 5, message = "평점은 5 이하여야 합니다.")
        Integer rating

) {
}
