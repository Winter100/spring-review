package com.project.review.review;

import com.project.review.review.service.ImageDeletionTime;
import com.project.review.review.service.ReviewImageKeys;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewImageKeysTests {
    @Test
    void collectsNestedInternalKeysOnceAndIgnoresExternalOrMissingSources() {
        var document = JsonMapper.builder().build().readTree("""
                {"type":"doc","content":[{"type":"blockquote","content":[
                  {"type":"image","attrs":{"src":"reviews/A"}},
                  {"type":"image","attrs":{"src":"https://images.example.com/reviews/B"}},
                  {"type":"image","attrs":{"src":"https://images.example.com.evil/reviews/C"}},
                  {"type":"image","attrs":{"src":"blob:local"}},
                  {"type":"image","attrs":{}},
                  {"type":"text","text":"reviews/text"}
                ]}]}
                """);
        assertThat(new ReviewImageKeys("https://images.example.com/")
                .collect("reviews/A", List.of("reviews/B"), document))
                .containsExactlyInAnyOrder("reviews/A", "reviews/B");
    }

    @Test
    void schedulesStrictlyNextKoreanMidnightAcrossBoundary() {
        assertThat(ImageDeletionTime.nextMidnight(Instant.parse("2026-11-02T14:59:59Z")))
                .isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        assertThat(ImageDeletionTime.nextMidnight(Instant.parse("2026-11-02T15:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
        assertThat(ImageDeletionTime.nextMidnight(Instant.parse("2026-11-02T15:00:01Z")))
                .isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
    }
}
