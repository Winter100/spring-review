package com.project.review.storage.service;

import com.project.review.storage.dtos.UploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service // Presigned URL 발급 비즈니스 로직
@RequiredArgsConstructor // S3Presigner 생성자 주입
public class StorageService {

    private final S3Presigner presigner; // URL 서명 객체

    @Value("${storage.bucket}")
    private String bucket; // 이미지 저장 버킷 이름

    @Value("${storage.public-base-url}")
    private String publicBaseUrl; // R2 공개 개발 URL 또는 버킷에 연결된 사용자 도메인

    public UploadResponse createUploadUrl(String contentType) {

        // 지원하는 이미지 MIME 타입만 허용
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
        }

        // UUID로 충돌 가능성이 매우 낮은 객체 키 생성
        String key = "reviews/" + UUID.randomUUID();

        // 업로드할 버킷, 객체 키, MIME 타입 정의
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket) // 저장 대상 버킷
                .key(key) // 이미지 객체 키
                .contentType(contentType) // 업로드 요청에 사용할 MIME 타입
                .build();

        // Presigned URL의 유효시간과 요청 조건 정의
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5)) // 유효기간 5분
                .putObjectRequest(putRequest) // PUT 업로드 요청 지정
                .build();

        // 서명된 업로드 URL 생성
        String url = presigner.presignPutObject(presignRequest)
                .url()
                .toString();

        // 업로드 URL과 객체 키 반환
        return new UploadResponse(url, key);
    }

    /** 공개 읽기가 허용된 객체의 고정 URL을 만듭니다. 파일 존재 여부는 확인하지 않습니다. */
    public String createReadUrl(String key) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
                .pathSegment(key.split("/"))
                .build()
                .encode()
                .toUriString();
    }
}