package com.project.review.category.dtos;

import jakarta.validation.constraints.NotNull;

public record CategoryRemoveRequest (
        @NotNull(message = "카테고리 아이디는 필수입니다.")
        Long id
) {
}
