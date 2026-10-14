package com.project.review.member.dtos;

import com.project.review.member.entities.Member;

public record MemberSummaryResponse(
        Long id,
        String nickname
) {
    public static MemberSummaryResponse from(Member member) {
        return new MemberSummaryResponse(
                member.getId(),
                member.getNickname()
        );
    }
}
