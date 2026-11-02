package com.project.review.review.service;

import com.project.review.review.entities.ImageDeleteStatus;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.storage.dtos.ImageDeletionResult;
import com.project.review.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.image-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewImageCleanup {
    private final ReviewImageDeleteRepository deletions;
    private final ReviewRepository reviews;
    private final ReviewImageKeys imageKeys;
    private final StorageService storage;
    private final TransactionTemplate transactions;
    private final Clock imageDeletionClock;
    private final ReentrantLock executionLock = new ReentrantLock();

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void deleteUnusedImages() {
        if (!executionLock.tryLock()) return;
        int total = 0;
        int deleted = 0;
        int failed = 0;
        int deferred = 0;
        try {
            Instant cutoff = imageDeletionClock.instant();
            long afterId = 0;
            while (true) {
                List<ReviewImageDelete> batch = deletions
                        .findByStatusAndNextAttemptAtLessThanEqualAndIdGreaterThanOrderByIdAsc(
                                ImageDeleteStatus.PENDING, cutoff, afterId, PageRequest.of(0, 1000));
                if (batch.isEmpty()) break;
                BatchCounts counts = processBatch(batch);
                total += batch.size();
                deleted += counts.deleted();
                failed += counts.failed();
                deferred += counts.deferred();
                afterId = batch.getLast().getId();
            }
        } catch (RuntimeException exception) {
            // R2 삭제 후 DB 기록이 실패해도 대기 행을 남겨 다음 실행에서 재시도합니다.
            log.error("리뷰 이미지 삭제 작업 중단: 미완료 작업은 다음 실행에서 재시도합니다.", exception);
        } finally {
            log.info("리뷰 이미지 삭제: 대상={}, 성공={}, 실패={}, 참조 보류={}",
                    total, deleted, failed, deferred);
            executionLock.unlock();
        }
    }

    private BatchCounts processBatch(List<ReviewImageDelete> batch) {
        Set<String> candidates = batch.stream().map(ReviewImageDelete::getImageKey).collect(Collectors.toSet());
        // 진행 중인 연결 트랜잭션이 있으면 완료를 기다린 뒤 현재 참조를 확인합니다.
        storage.preventPendingAttachment(candidates);
        Set<String> referenced = findReferences(candidates);
        candidates.removeAll(referenced);
        ImageDeletionResult deletionResult = deleteFromStorage(batch, candidates);
        Instant now = imageDeletionClock.instant();
        Instant retryAt = ImageDeletionTime.nextMidnight(now);
        transactions.executeWithoutResult(status -> {
            List<ReviewImageDelete> current = deletions.findAllById(batch.stream().map(ReviewImageDelete::getId).toList());
            for (ReviewImageDelete deletion : current) {
                String key = deletion.getImageKey();
                if (referenced.contains(key)) {
                    deletion.deferUntil(retryAt);
                    log.warn("사용 중인 이미지 삭제 보류: 작업 ID={}, 키={}", deletion.getId(), key);
                } else if (deletionResult.succeeded().contains(key)) {
                    deletion.markDeleted(now);
                } else {
                    deletion.scheduleRetry(retryAt);
                    log.warn("이미지 삭제 재시도 예약: 작업 ID={}, 오류={}", deletion.getId(), deletionResult.failed().get(key));
                }
            }
        });
        int deleted = (int) batch.stream().filter(row -> deletionResult.succeeded().contains(row.getImageKey())).count();
        int deferred = (int) batch.stream().filter(row -> referenced.contains(row.getImageKey())).count();
        return new BatchCounts(deleted, batch.size() - deleted - deferred, deferred);
    }

    private ImageDeletionResult deleteFromStorage(List<ReviewImageDelete> batch, Set<String> candidates) {
        try {
            return storage.deleteObjects(List.copyOf(candidates));
        } catch (RuntimeException exception) {
            log.warn("R2 이미지 일괄 삭제 실패: 작업 ID={}", batch.stream().map(ReviewImageDelete::getId).toList(), exception);
            var failed = new HashMap<String, String>();
            candidates.forEach(key -> failed.put(key, exception.getClass().getSimpleName()));
            return new ImageDeletionResult(Set.of(), failed);
        }
    }

    private Set<String> findReferences(Set<String> candidates) {
        Set<String> referenced = new HashSet<>(reviews.findReferencedImageKeys(candidates));
        long afterId = 0;
        // ponytail: 본문을 페이지 단위로 검사합니다. 데이터가 커지면 이미지 참조 인덱스 테이블로 전환합니다.
        while (referenced.size() < candidates.size()) {
            List<ReviewRepository.ContentReference> page = reviews.findContentReferences(afterId, PageRequest.of(0, 500));
            if (page.isEmpty()) break;
            for (var review : page) {
                Set<String> bodyKeys = imageKeys.fromContent(review.getContent());
                bodyKeys.retainAll(candidates);
                referenced.addAll(bodyKeys);
            }
            afterId = page.getLast().getId();
        }
        return referenced;
    }

    private record BatchCounts(int deleted, int failed, int deferred) {}
}
