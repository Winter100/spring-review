package com.project.review.storage.entities;

import lombok.Getter;

@Getter
public enum UploadStatus {
    PENDING("업로드 대기"),
    ATTACHED("리뷰 첨부 완료"),
    EXPIRED("업로드 만료");

    private final String description;

    UploadStatus(String description) {
        this.description = description; // enum 생성 시 설명 저장
    }
}
