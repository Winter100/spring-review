package com.project.review.review.service;

import com.project.review.review.repository.ReviewViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class ReviewViewCleanup {

    private final ReviewViewRepository reviewViewRepository;
    private final Clock reviewViewClock;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredViews() {
        // 오늘과 어제의 중복 방지 기록은 남기고 누적 조회수는 변경하지 않습니다.
        LocalDate today = LocalDate.now(reviewViewClock.withZone(ZoneId.of("Asia/Seoul")));
        reviewViewRepository.deleteBefore(today.minusDays(1));
    }
}
