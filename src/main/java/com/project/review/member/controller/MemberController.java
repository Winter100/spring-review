package com.project.review.member.controller;

import com.project.review.auth.service.TokenService;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final TokenService tokenService;

    @GetMapping
    public ResponseEntity<MemberResponse> me(
            @AuthenticationPrincipal Jwt jwt
    ){
        Long memberId = tokenService.extractMemberId(jwt);

        return ResponseEntity.ok().body(MemberResponse.from(memberService.findById(memberId)));
    }

}
