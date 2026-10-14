package com.project.review.review;

import com.project.review.member.MemberService;
import com.project.review.member.entities.Member;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewResponse;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final MemberService memberService;


    @Transactional(readOnly = true)
    public List<ReviewResponse> findAll(){
        return reviewRepository.findAll()
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional()
    public ReviewResponse create(ReviewRequest reviewRequest, Long memberId){
        Member member = memberService.findById(memberId);

        Review review = Review.builder()
                .title(reviewRequest.title())
                .content(reviewRequest.content())
                .rating(reviewRequest.rating())
                .author(member)
                .build();

         return ReviewResponse.from(reviewRepository.save(review));
    }
}
