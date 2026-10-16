package com.project.review.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record TokenProperties (
        Long accessTokenExpiresMinutes,
        Long refreshTokenExpiresDays
) {
}
