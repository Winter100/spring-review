package com.project.review.category.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "카테고리는 필수입니다.")
        @Size(max = 255, message = "카테고리는 255자 이하여야 합니다.")
        String category
) {
}
