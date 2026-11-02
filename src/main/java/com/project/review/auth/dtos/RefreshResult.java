package com.project.review.auth.dtos;

import java.time.Instant;

public record RefreshResult(
        String accessToken,
        String newRefreshToken,
        Instant expiresAt
) {
}
