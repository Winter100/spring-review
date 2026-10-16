package com.project.review.member.service;

import com.project.review.common.config.TokenProperties;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.dtos.LoginRequest;
import com.project.review.member.dtos.MemberRequest;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.entities.Member;
import com.project.review.member.entities.RefreshToken;
import com.project.review.member.exception.DuplicateMemberException;
import com.project.review.member.exception.RefreshTokenException;
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

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenProperties tokenProperties;

    public record LoginResult(String accessToken, String refreshToken, Member member) {
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

        String accessToken = tokenService.createAccessToken(member);
        String refreshToken = tokenService.createRefreshToken(member);
        Instant now = Instant.now();

        refreshTokenRepository.save(new RefreshToken(
                member,
                tokenService.hashRefreshToken(refreshToken),
                now.plus(tokenProperties.refreshTokenExpiresDays(), ChronoUnit.DAYS)
        ));

        return new LoginResult(accessToken, refreshToken, member);
    }

    // 만료 토큰을 삭제한 뒤 예외를 던져도 해당 삭제는 커밋합니다.
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public String refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RefreshTokenException(ErrorCode.REFRESH_NOT_FOUND);
        }

        // 서버가 발급하고 DB에 등록한 원문과 일치하는 토큰만 허용합니다.
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHash(tokenService.hashRefreshToken(refreshToken))
                .orElseThrow(() -> new RefreshTokenException(ErrorCode.REFRESH_UN_VALID));

        if (!storedToken.getExpiresAt().isAfter(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new RefreshTokenException(ErrorCode.REFRESH_EXPIRES);
        }

        return tokenService.createAccessToken(storedToken.getMember());
    }

    @Transactional(readOnly = true)
    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

}
