package com.project.review.member.dtos;

import com.project.review.member.entities.Member;

public record LoginResponse(
        MemberResponse member,
        String access_token
) {
    public static LoginResponse from(Member member, String accessToken) {
        return new LoginResponse(
                MemberResponse.from(member),
                accessToken
        );
    }
}
