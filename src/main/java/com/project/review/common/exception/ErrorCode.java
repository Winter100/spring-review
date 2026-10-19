package com.project.review.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter // 각 Enum 필드의 Getter 생성
@RequiredArgsConstructor // final 필드 생성자 생성
public enum ErrorCode {

    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."), // 리뷰 조회 실패
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."), // 카테고리 조회 실패
    CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 카테고리명입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."), // 회원 조회 실패
    INVALID_AUTHENTICATION(HttpStatus.UNAUTHORIZED, "유효하지 않은 회원 인증 정보입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."), // 접근 권한 부족
    IMAGE_UPLOAD_CONFLICT(HttpStatus.CONFLICT, "이미지 업로드 상태 또는 연결 기한이 변경되었습니다."),
    REFRESH_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 없습니다. 다시 로그인해 주세요."),
    REFRESH_UN_VALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다. 다시 로그인해 주세요."),
    REFRESH_EXPIRES(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "");

    private final HttpStatus status; // HTTP 상태 코드
    private final String message; // 기본 오류 메시지
}
