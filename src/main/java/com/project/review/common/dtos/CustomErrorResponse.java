package com.project.review.common.dtos;

import lombok.Builder;

import java.util.List;

@Builder
public record CustomErrorResponse(
        String message,
        List<FieldError> errors
) {
    public CustomErrorResponse(String message) {
        this(message, List.of());
    }

    public record FieldError(String field, String message) {
    }
}
