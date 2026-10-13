package com.project.review.common.dtos;

import lombok.Builder;
import org.springframework.http.HttpStatusCode;

@Builder
public record CustomErrorResponse(
        String message
) {
}
