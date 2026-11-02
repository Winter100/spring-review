package com.project.review.review;

import com.project.review.review.entities.ReviewImageDelete;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import com.project.review.review.service.ImageDeletionTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewImageDeleteRepositoryTests {

    @Test
    void schedulesDeletionAtNextKoreanMidnightByDefault() {
        Instant earliest = ImageDeletionTime.nextMidnight(Instant.now());

        ReviewImageDelete deletion = new ReviewImageDelete("reviews/image");

        Instant latest = ImageDeletionTime.nextMidnight(Instant.now());
        assertThat(deletion.getNextAttemptAt()).isBetween(earliest, latest);
    }

    @Test
    void usesExplicitDeletionAttemptTime() {
        Instant nextAttemptAt = Instant.parse("2026-11-01T00:00:00Z");

        ReviewImageDelete deletion = new ReviewImageDelete("reviews/image", nextAttemptAt);

        assertThat(deletion.getNextAttemptAt()).isEqualTo(nextAttemptAt);
    }
}
