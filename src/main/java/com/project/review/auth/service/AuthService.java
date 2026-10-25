package com.project.review.auth.service;

import com.project.review.auth.dtos.LoginRequest;
import com.project.review.auth.dtos.LoginResult;
import com.project.review.auth.dtos.SignupRequest;
import com.project.review.auth.entities.RefreshToken;
import com.project.review.auth.repository.RefreshTokenRepository;
import com.project.review.common.config.TokenProperties;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.entities.Member;
import com.project.review.member.exception.RefreshTokenException;
import com.project.review.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final MemberService memberService;
    private final TokenService tokenService;
    private final TokenProperties tokenProperties;


    @Transactional
    public LoginResult login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.email(),
                        loginRequest.password()
                )
        );

        Member member = memberService.findByEmail(authentication.getName());
        String accessToken = tokenService.createAccessToken(member);
        String refreshToken = tokenService.createRefreshToken(member);
        Instant now = Instant.now();
        Duration refreshTokenExpiresDays = Duration.ofDays(tokenProperties.refreshTokenExpiresDays());

        refreshTokenRepository.save(
                new RefreshToken(
                        member,
                        tokenService.hashRefreshToken(refreshToken),
                        now.plus(refreshTokenExpiresDays)
                ));

        return new LoginResult(accessToken, refreshToken, refreshTokenExpiresDays, member);
    }

    @Transactional
    public MemberResponse signup(SignupRequest signupRequest) {
        return memberService.create(signupRequest);
    }

    // 만료 토큰을 삭제한 뒤 예외를 던져도 해당 삭제는 커밋합니다.
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public String refreshAccessToken(String refreshToken){
        isBlankTokenOrElseThrow(refreshToken);

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

    private void isBlankTokenOrElseThrow(String token){
        if (token == null || token.isBlank()) {
            throw new RefreshTokenException(ErrorCode.REFRESH_NOT_FOUND);
        }
    }

}
