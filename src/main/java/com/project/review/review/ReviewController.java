package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<List<ReviewResponse>> findAll(){
        return ResponseEntity.ok(reviewService.findAll());
    }

    @PostMapping()
    public ResponseEntity<ReviewResponse> create(
            @Valid @RequestBody ReviewRequest reviewRequest,
            @AuthenticationPrincipal Jwt jwt
            ){

        Long memberId = Long.valueOf(jwt.getSubject());

        ReviewResponse review = reviewService.create(reviewRequest, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);

    }
}
