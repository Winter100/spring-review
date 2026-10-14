package com.project.review.storage.dtos;

public record UploadResponse(
        String url, // 이미지 파일을 업로드할 임시 URL
        String key // 이미지 식별 및 DB 저장에 사용할 객체 키
) {}