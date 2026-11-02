package com.project.review.auth.service;

import com.project.review.auth.dtos.LoginRequest;
import com.project.review.auth.dtos.LoginResult;
import com.project.review.auth.dtos.RefreshResult;
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
import org.springframework.security.oauth2.jwt.Jwt;
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
        String tokenHash = tokenService.hashRefreshToken(refreshToken);

        Duration lifetime =
                Duration.ofDays(tokenProperties.refreshTokenExpiresDays());
        Instant expiresAt = Instant.now().plus(lifetime);

        RefreshToken storedToken = refreshTokenRepository
                .findByMemberId(member.getId())
                .orElse(null);

        if (storedToken == null) {
            refreshTokenRepository.save(
                    new RefreshToken(member, tokenHash, expiresAt)
            );
        } else {
            storedToken.renew(tokenHash, expiresAt);
        }

        return new LoginResult(accessToken, refreshToken, lifetime, member);
    }

    @Transactional
    public void logout(String refreshToken) {
        isBlankTokenOrElseThrow(refreshToken);

        String tokenHash = tokenService.hashRefreshToken(refreshToken);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
    }

    @Transactional
    public MemberResponse signup(SignupRequest signupRequest) {
        return memberService.create(signupRequest);
    }

    // 만료 토큰을 삭제한 뒤 예외를 던져도 해당 삭제는 커밋합니다.
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public RefreshResult refreshAccessToken(String refreshToken) {

        isBlankTokenOrElseThrow(refreshToken);
        Jwt jwt = tokenService.decodeRefreshToken(refreshToken);

        Instant now = Instant.now();

        if (jwt.getExpiresAt() == null
                || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))) {
            throw new RefreshTokenException(ErrorCode.REFRESH_INVALID);
        }

        // 서버가 발급하고 DB에 등록한 원문과 일치하는 토큰만 허용합니다.
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHash(tokenService.hashRefreshToken(refreshToken))
                .orElseThrow(() -> new RefreshTokenException(ErrorCode.REFRESH_INVALID));

        if (!jwt.getExpiresAt().isAfter(now) || !storedToken.getExpiresAt().isAfter(now)) {
            refreshTokenRepository.delete(storedToken);
            throw new RefreshTokenException(ErrorCode.REFRESH_INVALID);
        }

        Member member = storedToken.getMember();

        Instant expiresAt = jwt.getExpiresAt().isBefore(storedToken.getExpiresAt())
                ? jwt.getExpiresAt()
                : storedToken.getExpiresAt();

        String newRefreshToken = tokenService.createRefreshToken(member, expiresAt);
        String accessToken = tokenService.createAccessToken(member);

        storedToken.rotationToken(
                tokenService.hashRefreshToken(newRefreshToken)
        );

        return new RefreshResult(accessToken, newRefreshToken, expiresAt);
    }

    private void isBlankTokenOrElseThrow(String token) {
        if (token == null || token.isBlank()) {
            throw new RefreshTokenException(ErrorCode.REFRESH_INVALID);
        }
    }

}
