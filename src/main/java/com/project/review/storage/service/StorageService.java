package com.project.review.storage.service;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.service.MemberService;
import com.project.review.storage.constant.ImageContentType;
import com.project.review.storage.dtos.ImageUploadBatchRequest;
import com.project.review.storage.dtos.ImageUploadRequest;
import com.project.review.storage.dtos.ImageUploadResponse;
import com.project.review.storage.dtos.UploadVerification;
import com.project.review.storage.entities.ImageUploadStatus;
import com.project.review.storage.entities.UploadStatus;
import com.project.review.storage.repository.ImageUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service // Presigned URL 발급 비즈니스 로직
@RequiredArgsConstructor // S3Presigner 생성자 주입
public class StorageService {

    private final ImageUploadRepository imageUploadRepository;
    private final TransactionTemplate transactionTemplate;
    private final MemberService memberService;
    private final S3Presigner resigned; // URL 서명 객체
    private final S3Client s3Client;

    @Value("${storage.bucket}")
    private String bucket; // 이미지 저장 버킷 이름

    @Value("${storage.public-base-url}")
    private String publicBaseUrl; // R2 공개 개발 URL 또는 버킷에 연결된 사용자 도메인

    @Transactional
    public List<ImageUploadResponse> uploadUrls(ImageUploadBatchRequest batchRequest, Long memberId) {

        List<ImageUploadResponse> responseList = new ArrayList<>();
        List<ImageUploadStatus> imageUploadStatusList = new ArrayList<>();
        Member member = memberService.findById(memberId);
        Instant now = Instant.now();
        Instant attachExpiresAt = now.plus(Duration.ofHours(1));

        for(ImageUploadRequest request: batchRequest.images()){

            checkContentTypeAndFileSize(request.contentType(), request.fileSize());

            // UUID로 충돌 가능성이 매우 낮은 객체 키 생성
            String key = "reviews/" + UUID.randomUUID();

            // 업로드할 버킷, 객체 키, MIME 타입 정의
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket) // 저장 대상 버킷
                    .key(key) // 이미지 객체 키
                    .contentType(request.contentType()) // 업로드 요청에 사용할 MIME 타입
                    .build();

            // Presigned URL의 유효시간과 요청 조건 정의
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(5)) // 유효기간 5분
                    .putObjectRequest(putRequest) // PUT 업로드 요청 지정
                    .build();

            // 서명된 업로드 URL 생성
            PresignedPutObjectRequest presignedRequest = resigned.presignPutObject(presignRequest);
            String url = presignedRequest.url().toString();

            // 발급 조건과 실제 서명 만료 시각을 저장합니다.
            ImageUploadStatus imageUploadStatus = new ImageUploadStatus(
                    member,
                    key,
                    request.contentType(),
                    request.fileSize(),
                    UploadStatus.PENDING,
                    presignedRequest.expiration(),
                    attachExpiresAt
            );

            responseList.add(new ImageUploadResponse(url, key));
            imageUploadStatusList.add(imageUploadStatus);
        }

        imageUploadRepository.saveAll(imageUploadStatusList);

        // 업로드 URL과 객체 키 반환
        return responseList;
    }

    private void checkContentTypeAndFileSize(String contentType, Long fileSize){
        // 지원하는 이미지 MIME 타입만 허용
        if (contentType == null || !ImageContentType.ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
        }

        // 파일 사이즈 확인
        if(fileSize > 5 * 1024 * 1024){
            throw new IllegalArgumentException("최대 5MB까지 가능합니다.");
        }
    }

    public void validateImagesForReview(List<String> imageKeys, Long memberId) {
        if (!imageKeys.isEmpty()) {
            // 이미지 업로드 생성시 요청한 이미지와 실제 리뷰 요청시 들어온 이미지 확인
            List<UploadVerification> uploads = transactionTemplate.execute(status ->
                    validateAttachableUploads(imageKeys, memberId));

            // 실제 R2 Head와 비교
            validateUploadedObjects(uploads);
        }
    }

    public void attachIfPending(List<String> imageKeys, Long memberId){
        for (String key : imageKeys.stream().sorted().toList()) {
            int updated = imageUploadRepository.attachIfPending(
                    key, memberId, Instant.now(), UploadStatus.PENDING, UploadStatus.ATTACHED);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.IMAGE_UPLOAD_CONFLICT);
            }
        }
    }
    /**
     * 이미지 테이블과 키, 본인, 기한 등 확인
     */
    private List<UploadVerification> validateAttachableUploads(
            List<String> imageKeys,
            Long memberId
    ) {
        if (imageKeys.isEmpty()) {
            return List.of();
        }
        if (new HashSet<>(imageKeys).size() != imageKeys.size()) {
            throw new IllegalArgumentException("이미지 키가 중복되었습니다.");
        }

        // 업로드 요청시 생성한 이미지 키 데이터
        List<ImageUploadStatus> uploads = imageUploadRepository.findAllByImageKeyIn(imageKeys);

        // 요청시 생성한 데이터와 리뷰 생성 요청 이미지 키 비교
        if (uploads.size() != imageKeys.size()) {
            throw new IllegalArgumentException("발급되지 않은 이미지 키가 있습니다.");
        }

        Instant now = Instant.now();

        for (ImageUploadStatus upload : uploads) {
            if (!upload.getMember().getId().equals(memberId)) {
                throw new IllegalArgumentException("본인의 업로드만 사용할 수 있습니다.");
            }

            if (upload.getStatus() != UploadStatus.PENDING) {
                throw new IllegalArgumentException("연결할 수 없는 업로드 상태입니다.");
            }

            if (!upload.getAttachExpiresAt().isAfter(now)) {
                throw new IllegalArgumentException("이미지 연결 기한이 만료되었습니다.");
            }
        }

        return uploads.stream()
                .map(upload -> new UploadVerification(upload.getImageKey(), upload.getExpectedSize(),
                        upload.getExpectedContentType()))
                .toList();
    }

    private void validateUploadedObjects(List<UploadVerification> uploads) {
        // Todo 실패 하면 이미지 삭제 해야함.
        // 바로 삭제하지말고 이미지 삭제 테이블에 키 등록하기?
        for (UploadVerification upload : uploads) {
            HeadObjectResponse object = s3Client.headObject(builder -> builder
                    .bucket(bucket)
                    .key(upload.imageKey()));

            Long actualSize = object.contentLength();

            if (actualSize == null || actualSize <= 0 || actualSize > 5L * 1024 * 1024) {
                throw new IllegalArgumentException("이미지 크기가 허용 범위를 벗어났습니다.");
            }

            if (!actualSize.equals(upload.expectedSize())) {
                throw new IllegalArgumentException("업로드한 이미지 크기가 발급 요청과 다릅니다.");
            }

            if (!upload.expectedContentType().equals(object.contentType())) {
                throw new IllegalArgumentException("업로드한 이미지 타입이 발급 요청과 다릅니다.");
            }
        }
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
