package com.project.review.common.exception;

import lombok.Getter;

@Getter // ErrorCode Getter 생성
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode; // 발생한 오류 종류

    // 1. 기본 메시지를 사용하는 경우
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage()); // ErrorCode의 기본 메시지 사용
        this.errorCode = errorCode; // 오류 코드 저장
    }

    // 2. 직접 메시지를 전달하는 경우
    public BusinessException(ErrorCode errorCode, String message) {
        super(message); // 전달받은 메시지 사용
        this.errorCode = errorCode; // 오류 코드 저장
    }
}
