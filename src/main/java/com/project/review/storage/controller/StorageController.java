package com.project.review.storage.controller;

import com.project.review.member.service.TokenService;
import com.project.review.storage.dtos.ImageUploadRequest;
import com.project.review.storage.dtos.ImageUploadResponse;
import com.project.review.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final TokenService tokenService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/presigned-url")
    public ResponseEntity<ImageUploadResponse> uploadUrl(
            @Valid @RequestBody ImageUploadRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 서비스 호출 후 발급 결과를 HTTP 200으로 반환
        Long memberId = tokenService.extractMemberId(jwt);
        return ResponseEntity.ok(
                storageService.uploadUrl(request, memberId)
        );
    }
}