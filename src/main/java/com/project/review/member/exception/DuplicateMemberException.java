package com.project.review.member.exception;

/** 가입 시 이메일 또는 닉네임이 기존 회원과 충돌한 경우에 사용합니다. */
public class DuplicateMemberException extends RuntimeException {

    public DuplicateMemberException(String message) {
        super(message);
    }
}
