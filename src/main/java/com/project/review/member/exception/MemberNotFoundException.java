package com.project.review.member.exception;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(){
        super("회원이 존재하지 않습니다.");
    }
}
