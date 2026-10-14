package com.project.review.member;

import com.project.review.member.dtos.LoginRequest;
import com.project.review.member.dtos.MemberRequest;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.entities.Member;
import com.project.review.member.entities.RefreshToken;
import com.project.review.member.exception.MemberNotFoundException;
import com.project.review.member.repository.MemberRepository;
import com.project.review.member.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;

    public record LoginResult(String accessToken, String refreshToken) {
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {

        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateMemberException("이미 가입된 이메일 입니다.");
        }

        if (memberRepository.existsByNickname(request.nickname())) {
            throw new DuplicateMemberException("이미 생성된 닉네임 입니다.");
        }

        Member member = Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public LoginResult login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.email(),
                        loginRequest.password()
                )
        );

        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("회원이 존재하지 않습니다."));

        String accessToken = tokenService.createAccessToken(member.getId());
        String refreshToken = tokenService.createRefreshToken();
        Instant now = Instant.now();

        refreshTokenRepository.save(new RefreshToken(
                member,
                tokenService.hashRefreshToken(refreshToken),
                now.plus(7, ChronoUnit.DAYS)
        ));

        return new LoginResult(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public Member findById(Long memberId){
         return memberRepository.findById(memberId)
                 .orElseThrow(MemberNotFoundException::new);
    }

}
