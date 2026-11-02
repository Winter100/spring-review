package com.project.review.review.service;

import java.time.Instant;
import java.time.ZoneId;

public final class ImageDeletionTime {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private ImageDeletionTime() {}

    public static Instant nextMidnight(Instant now) {
        return now.atZone(SEOUL).toLocalDate().plusDays(1).atStartOfDay(SEOUL).toInstant();
    }
}
