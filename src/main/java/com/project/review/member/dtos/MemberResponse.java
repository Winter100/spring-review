package com.project.review.member.dtos;

import com.project.review.member.entities.Member;

public record MemberResponse (
        Long id,
        String email,
        String nickname
) {

    public static MemberResponse from(Member member){
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname());
    }
}
