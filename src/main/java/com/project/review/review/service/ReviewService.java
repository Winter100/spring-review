package com.project.review.review.service;

import com.project.review.category.entities.Category;
import com.project.review.category.service.CategoryService;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.service.MemberService;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewResponse;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImage;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final MemberService memberService;
    private final CategoryService categoryService;
    private final StorageService storageService;

    // 모든 리뷰 조회
    @Transactional(readOnly = true)
    public List<ReviewResponse> findAll() {
        return reviewRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 특정 리뷰 조회
    @Transactional(readOnly = true)
    public ReviewResponse findById(Long reviewId) {
        return toResponse(findReviewById(reviewId));
    }

    // 이미지 저장에 실패하면 리뷰 저장도 함께 롤백합니다.
    @Transactional
    public ReviewResponse create(ReviewRequest reviewRequest, Long memberId) {
        Member member = memberService.findById(memberId);
        Category category = categoryService.findById(reviewRequest.categoryId());

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
    public void remove(Long reviewId, Long memberId) {
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
