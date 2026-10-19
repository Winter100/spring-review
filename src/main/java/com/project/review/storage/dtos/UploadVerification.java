package com.project.review.storage.dtos;

/** DB 조회 트랜잭션 밖에서 R2 객체와 비교할 발급 정보입니다. */
public record UploadVerification(String imageKey, Long expectedSize, String expectedContentType) {
}
