package com.project.review.member.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 멤버 dto
 * 이메일
 * 비밀번호
 * 닉네임
 * Todo 프로필 이미지
 * */


public record MemberRequest (
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20,message = "8 ~ 20자")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20,message = "2 ~ 20자")
        String nickname
) {
}
