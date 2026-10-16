package com.project.review.member.exception;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;

/** 재로그인이 필요한 refresh token 오류를 로그인 자격 증명 오류와 구분합니다. */
public class RefreshTokenException extends BusinessException {

    public RefreshTokenException(ErrorCode errorCode) {
        super(errorCode);
    }
}
