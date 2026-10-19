package com.project.review.storage.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ImageUploadRequest(
        @NotBlank(message = "contentType은 필수입니다.")
        String contentType, // image/jpeg, image/png, image/webp 등

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = 5 * 1024 * 1024, message = "이미지 크기는 최대 5MB입니다.")
        Long fileSize // 바이트 단위
) {}
