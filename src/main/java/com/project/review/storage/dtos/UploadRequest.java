package com.project.review.storage.dtos;

public record UploadRequest(
        String contentType // image/jpeg, image/png, image/webp 등
) {}