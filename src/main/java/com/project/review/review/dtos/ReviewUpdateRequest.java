package com.project.review.review.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 리뷰 수정 요청입니다.
 */

@Schema(description = "리뷰 수정 요청")
public record ReviewUpdateRequest(

        @Schema(description = "리뷰 제목", example = "이 제품 사용 후기!")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(min = 1, max = 30, message = "제목은 1~30자 사이입니다.")
        String title,

        @Schema(description = "리뷰 내용", example = "정말 만족 스러워요!")
        @NotBlank(message = "내용은 필수입니다.")
        @Size(min = 1, max = 5000, message = "내용은 1~5000자 사이입니다.")
        String content,

        @Schema(description = "평점: 0~5 사이의 숫자 (소수 허용)", example = "3.5", minimum = "0", maximum = "5")
        @NotNull(message = "평점은 필수입니다.")
        @DecimalMin(value = "0", message = "평점은 0 이상이어야 합니다.")
        @DecimalMax(value = "5", message = "평점은 5 이하여야 합니다.")
        BigDecimal rating,

        @Schema(description = "카테고리 아이디", example = "2")
        @NotNull(message = "카테고리 아이디는 필수입니다.")
        @Positive(message = "카테고리 아이디는 양수여야 합니다.")
        Long categoryId,

        @Schema(description = "대표 이미지 객체 키 (선택)", example = "reviews/main-image-key")
        @Pattern(regexp = "(?s).*\\S.*", message = "대표 이미지 키는 비어 있을 수 없습니다.")
        @Size(max = 1024, message = "대표 이미지 키는 최대 1024자입니다.")
        String mainImageKey,

        @Schema(description = "서브 이미지 객체 키 목록 (선택, 최대 4개)")
        @Size(max = 4, message = "서브 이미지는 최대 4개까지 등록할 수 있습니다.")
        List<@NotBlank(message = "서브 이미지 키는 비어 있을 수 없습니다.")
        @Size(max = 1024, message = "서브 이미지 키는 최대 1024자입니다.") String> subImageKeys
) {
    @JsonIgnore
    @AssertTrue(message = "서브 이미지를 등록하려면 대표 이미지가 필요합니다.")
    public boolean isImageCompositionValid() {
        return subImageKeys == null || subImageKeys.isEmpty() || mainImageKey != null && !mainImageKey.isBlank();
    }
}
