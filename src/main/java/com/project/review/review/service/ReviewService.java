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
import com.project.review.review.dtos.ReviewUpdateRequest;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImage;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.storage.repository.ImageUploadRepository;
import com.project.review.storage.service.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final MemberService memberService;
    private final CategoryService categoryService;
    private final StorageService storageService;
    private final TokenService tokenService;
    private final ReviewRepository reviewRepository;
    private final ImageUploadRepository imageUploadRepository;
    private final ReviewImageDeleteRepository reviewImageDeleteRepository;

    /**
     *  모든 리뷰 조회
     *  Todo:(메인 페이지) 실제 목록에 맞춰 toResponse 수정, 레퍼지토리 수정하기
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> findAll() {
        return reviewRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 상세 리뷰 조회
     */
    @Transactional(readOnly = true)
    public ReviewResponse findById(Long reviewId) {
        return toResponse(findReviewById(reviewId));
    }

    /**
     * 리뷰 생성
     * 업로드된 이미지의 첨부 가능 여부 및 R2 메타데이터를 검증한 뒤 리뷰 생성
     * 업로드 상태변경 및 리뷰 저장만 하나의 트랜잭션으로 처리
     */
    public ReviewResponse create(ReviewRequest reviewRequest, Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);
        Member member = memberService.findById(memberId);
        Category category = categoryService.findById(reviewRequest.categoryId());

        // 메인, 서브 이미지 키를 하나의 리스트로 반환
        List<String> imageKeys = collectImageKeys(reviewRequest.mainImageKey(), reviewRequest.subImageKeys());

        // 요청 이미지 키와 실제 R2 메타데이터 검증
        storageService.validateImagesForReview(imageKeys, memberId);

        return transactionTemplate.execute(status ->
                saveReview(reviewRequest, member, category, imageKeys));
    }

    /**
     * 리뷰 수정
     * - PUT 방식 기존 리뷰 -> 새로운 리뷰로 교체
     * - 이미지만 변경되어도 리뷰의 버전 증가
     */
    public void update(Long reviewId, ReviewUpdateRequest reviewUpdateRequest, Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);
        Member member = memberService.findById(memberId);
        Review review = findReviewById(reviewId);

        validateReviewAuthor(review, memberId);
        Long expectedVersion = review.getVersion();

        // 수정 요청에서 이미지 리스트 가져오기
        Set<String> requestImages = new HashSet<>(collectImageKeys(reviewUpdateRequest.mainImageKey(), reviewUpdateRequest.subImageKeys()));

        // 기존 리뷰에서 이미지 리스트 가져오기
        Set<String> existingImages = new HashSet<>(collectImageKeys(review));

        // 새로 생성 되는 이미지 키 분리 -> 이미지 등록 요청 시 생성된 이미지 테이블 검증 -> R2 검증
        List<String> createImageKeys = requestImages.stream().filter(key -> !existingImages.contains(key)).toList();
        storageService.validateImagesForReview(createImageKeys, memberId);

        // 이미지 삭제 테이블에 등록되어야 하는 이미지 키 분리
        List<String> removeImageKeys = existingImages.stream().filter(key -> !requestImages.contains(key)).toList();
        List<ReviewImageDelete> deleteImages = removeImageKeys.stream()
                .map(ReviewImageDelete::new)
                .toList();

        // 리뷰 수정 트랜잭션
        transactionTemplate.executeWithoutResult(status -> {
            Review currentReview = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_UPDATE_CONFLICT));
            if (!currentReview.getVersion().equals(expectedVersion)) {
                throw new BusinessException(ErrorCode.REVIEW_UPDATE_CONFLICT);
            }
            // 이미지 관계만 변경해도 버전을 증가시켜 다른 수정·삭제와의 충돌을 감지합니다.
            entityManager.lock(currentReview, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

            storageService.attachIfPending(createImageKeys, member.getId());
            reviewImageDeleteRepository.saveAll(deleteImages);
            updateReview(currentReview, reviewUpdateRequest);
        });
    }

    /**
     * 리뷰 삭제
     * - 요청자와 리뷰 작성자 검증
     * - 삭제될 리뷰의 이미지 키를 이미지 삭제 예정 테이블에 등록 후 리뷰 삭제
     */
    @Transactional
    public void remove(Long reviewId, Jwt jwt) {
        Long memberId = tokenService.extractMemberId(jwt);

        Review review = findReviewById(reviewId);

        validateReviewAuthor(review, memberId);

        // 리뷰에서 이미지 키를 받고 삭제 테이블에 맞게 생성
        List<ReviewImageDelete> deleteImages = collectImageKeys(review).stream()
                .map(ReviewImageDelete::new)
                .toList();

        // 이미지 삭제 예정 테이블에 등록
        reviewImageDeleteRepository.saveAll(deleteImages);

        // 리뷰 삭제
        reviewRepository.delete(review);
    }

    private ReviewResponse saveReview(ReviewRequest reviewRequest, Member member, Category category, List<String> imageKeys) {

        // 이미지 저장 요청시 Pending으로 저장되었던 요청을 attach로 변경
        storageService.attachIfPending(imageKeys, member.getId());

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

    private void updateReview(Review review, ReviewUpdateRequest reviewUpdateRequest) {
        Category category = categoryService.findById(reviewUpdateRequest.categoryId());
        review.updateDetails(
                reviewUpdateRequest.title(),
                reviewUpdateRequest.content(),
                reviewUpdateRequest.rating(),
                category
        );

        review.clearImages();

        reviewRepository.flush();

        addReviewImages(
                review,
                reviewUpdateRequest.mainImageKey(),
                reviewUpdateRequest.subImageKeys()
        );
    }

    private Review findReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    // 이미지 리스트 키 추출 및 반환
    private List<String> collectImageKeys(String mainImageKey, List<String> subImageKeys) {
        List<String> imageKeys = new ArrayList<>();

        if (mainImageKey != null) {
            imageKeys.add(mainImageKey);
        }
        if (subImageKeys != null) {
            imageKeys.addAll(subImageKeys);
        }
        if (new HashSet<>(imageKeys).size() != imageKeys.size()) {
            throw new IllegalArgumentException("같은 이미지를 중복 등록할 수 없습니다.");
        }

        return imageKeys;
    }

    private List<String> collectImageKeys(Review review) {
        return review.getImages().stream()
                .map(ReviewImage::getImageKey)
                .toList();
    }

    private void validateReviewAuthor(Review review, Long memberId) {
        if (!review.getAuthor().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private  void addReviewImages(Review review, String mainImage, List<String> subImages){
        if (mainImage != null) {
            review.addImage(mainImage, 0);
        }

        if (subImages != null) {
            for (int index = 0; index < subImages.size(); index++) {
                review.addImage(subImages.get(index), index + 1);
            }
        }
    }

    private ReviewResponse toResponse(Review review) {
        List<ReviewImage> images = review.getImages();
        String mainImageKey = images.stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .findFirst()
                .map(ReviewImage::getImageKey)
                .orElse(null);

        List<String> subImageKeys = images.stream()
                .filter(image -> image.getDisplayOrder() > 0)
                .map(ReviewImage::getImageKey)
                .toList();

        return ReviewResponse.from(review, mainImageKey, subImageKeys);
    }
}
