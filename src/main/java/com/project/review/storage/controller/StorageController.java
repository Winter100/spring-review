package com.project.review.storage.controller;

import com.project.review.storage.dtos.UploadRequest;
import com.project.review.storage.dtos.UploadResponse;
import com.project.review.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // REST API 컨트롤러 등록
@RequestMapping("/api/v1/storage") // 공통 URL 경로
@RequiredArgsConstructor // StorageService 생성자 주입
public class StorageController {

    private final StorageService storageService; // URL 발급 서비스

    @PostMapping("/presigned-url") // POST /api/storage/presigned-url
    public ResponseEntity<UploadResponse> createUploadUrl(
            @RequestBody UploadRequest request // 요청 JSON을 DTO로 변환
    ) {
        // 서비스 호출 후 발급 결과를 HTTP 200으로 반환
        return ResponseEntity.ok(
                storageService.createUploadUrl(request.contentType())
        );
    }
}