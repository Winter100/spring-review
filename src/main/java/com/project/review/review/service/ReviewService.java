package com.project.review.review.service;

import com.project.review.category.entities.Category;
import com.project.review.category.service.CategoryService;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.service.MemberService;
import com.project.review.member.service.TokenService;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewResponse;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImage;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.storage.entities.UploadStatus;
import com.project.review.storage.repository.ImageUploadRepository;
import com.project.review.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final MemberService memberService;
    private final CategoryService categoryService;
    private final StorageService storageService;
    private final TokenService tokenService;
    private final ImageUploadRepository imageUploadRepository;
    private final TransactionTemplate transactionTemplate;

    // 모든 리뷰 조회
    @Transactional(readOnly = true)
    public List<ReviewResponse> findAll() {
        return reviewRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }


    @Transactional(readOnly = true)
    public ReviewResponse findById(Long reviewId) {
        return toResponse(findReviewById(reviewId));
    }

    /** 리뷰 생성
     * 업로드된 이미지의 첨부 가능 여부 및 R2 메타데이터를 검증한 뒤 리뷰 생성
     * 업로드 상태변경 및 리뷰 저장만 하나의 트랜잭션으로 처리
     */
    public ReviewResponse create(ReviewRequest reviewRequest, Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);
        Member member = memberService.findById(memberId);
        Category category = categoryService.findById(reviewRequest.categoryId());

        // 요청에서 메인, 서브 이미지 키 추출
        List<String> imageKeys = collectImageKeys(reviewRequest);

        // 요청 이미지 키와 실제 R2 메타데이터 검증
        storageService.validateImagesForReview(imageKeys, memberId);

        return transactionTemplate.execute(status ->
                saveReview(reviewRequest, member, category, imageKeys));
    }

    private ReviewResponse saveReview(ReviewRequest reviewRequest, Member member, Category category, List<String> imageKeys) {
        // 키 순서를 고정해 여러 이미지에 대한 동시 요청의 잠금 충돌을 줄입니다.
        for (String key : imageKeys.stream().sorted().toList()) {
            int updated = imageUploadRepository.attachIfPending(
                    key, member.getId(), Instant.now(), UploadStatus.PENDING, UploadStatus.ATTACHED);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.IMAGE_UPLOAD_CONFLICT);
            }
        }

        Review review = Review.builder()
                .title(reviewRequest.title())
                .content(reviewRequest.content())
                .rating(reviewRequest.rating())
                .author(member)
                .category(category)
                .build();

        String mainImageKey = reviewRequest.mainImageKey();
        List<String> subImageKeys = reviewRequest.subImageKeys();

        if (mainImageKey != null) {
            review.addImage(mainImageKey, 0);
        }

        if (subImageKeys != null) {
            for (int index = 0; index < subImageKeys.size(); index++) {
                review.addImage(subImageKeys.get(index), index + 1);
            }
        }

        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public void remove(Long reviewId, Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);

        Review review = reviewRepository.findForDeleteById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getAuthor().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        reviewRepository.delete(review);
    }

    private Review findReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    // 이미지 리스트 키 추출 및 반환
    private List<String> collectImageKeys(ReviewRequest reviewRequest) {
        List<String> imageKeys = new ArrayList<>();

        if (reviewRequest.mainImageKey() != null) {
            imageKeys.add(reviewRequest.mainImageKey());
        }
        if (reviewRequest.subImageKeys() != null) {
            imageKeys.addAll(reviewRequest.subImageKeys());
        }
        if (new HashSet<>(imageKeys).size() != imageKeys.size()) {
            throw new IllegalArgumentException("같은 이미지를 중복 등록할 수 없습니다.");
        }

        return imageKeys;
    }



    private ReviewResponse toResponse(Review review) {
        List<ReviewImage> images = review.getImages();
        String mainImageUrl = images.stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .map(image -> storageService.createReadUrl(image.getImageKey()))
                .findFirst()
                .orElse(null);
        List<String> subImageUrls = images.stream()
                .filter(image -> image.getDisplayOrder() > 0)
                .map(image -> storageService.createReadUrl(image.getImageKey()))
                .toList();

        return ReviewResponse.from(review, mainImageUrl, subImageUrls);
    }
}
