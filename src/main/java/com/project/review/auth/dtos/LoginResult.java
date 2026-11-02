package com.project.review.auth.dtos;

import com.project.review.member.entities.Member;

import java.time.Duration;

public record LoginResult(
        String accessToken,
        String refreshToken,
        Duration lifetime,
        Member member)
{}
