package com.project.review.auth.dtos;

import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.entities.Member;

public record LoginResponse(
        MemberResponse user,
        String accessToken
) {
    public static LoginResponse from(Member member, String accessToken) {
        return new LoginResponse(
                MemberResponse.from(member),
                accessToken
        );
    }
}
