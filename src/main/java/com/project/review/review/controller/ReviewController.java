package com.project.review.review.controller;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewResponse;
import com.project.review.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Review", description = "리뷰 목록 조회 및 생성 API")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "모든 리뷰 조회",
            description = "인증 없이 모든 리뷰와 공개 이미지 URL을 조회합니다. 리뷰가 없으면 빈 배열을 반환합니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> findAll() {
        return ResponseEntity.ok(reviewService.findAll());
    }

    @Operation(summary = "리뷰 상세 조회", description = "인증 없이 리뷰 ID로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "ID가 Long으로 변환할 수 없는 형식"),
            @ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> findById(
            @Parameter(description = "리뷰 ID", example = "1") @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok().body(reviewService.findById(id));
    }

    @Operation(
            summary = "리뷰 생성",
            description = "인증된 회원의 리뷰와 대표 이미지 키 1개, 서브 이미지 키 최대 4개를 저장합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공 및 공개 이미지 URL 반환"),
            @ApiResponse(responseCode = "400", description = "리뷰 또는 이미지 키 검증 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요 또는 잘못된 회원 인증 정보"),
            @ApiResponse(responseCode = "404", description = "작성자 또는 카테고리를 찾을 수 없음")
    })
    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            @Valid @RequestBody ReviewRequest reviewRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = extractMemberId(jwt);
        ReviewResponse review = reviewService.create(reviewRequest, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    // 서명 검증된 JWT라도 sub가 양수인 회원 ID인지 확인합니다.
    private Long extractMemberId(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
        }
        try {
            long memberId = Long.parseLong(jwt.getSubject());
            if (memberId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
            }
            return memberId;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
        }
    }
}
