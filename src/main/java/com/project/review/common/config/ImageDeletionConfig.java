package com.project.review.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class ImageDeletionConfig {
    @Bean
    public Clock imageDeletionClock() {
        return Clock.systemUTC();
    }
}
