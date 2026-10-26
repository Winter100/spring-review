package com.project.review.auth.service;

import com.project.review.common.config.TokenProperties;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.exception.RefreshTokenException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class TokenService {

    @Value("${security.jwt.issuer}")
    private String issuer;
    private final JwtEncoder jwtEncoder;
    private final TokenProperties tokenProperties;
    private final JwtDecoder refreshTokenDecoder;

    public TokenService(
            JwtEncoder jwtEncoder,
            TokenProperties tokenProperties,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder
    ) {
        this.jwtEncoder = jwtEncoder;
        this.tokenProperties = tokenProperties;
        this.refreshTokenDecoder = refreshTokenDecoder;
    }

    public Jwt decodeRefreshToken(String refreshToken) {
        try {
            return refreshTokenDecoder.decode(refreshToken);
        } catch (JwtException exception) {
            throw new RefreshTokenException(ErrorCode.REFRESH_UN_VALID);
        }
    }

    public String createAccessToken(Member member) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(member.getId().toString())
                .claim("role", member.getRole().name())
                .claim("token_type","access")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenProperties.accessTokenExpiresMinutes(), ChronoUnit.MINUTES))
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(jwsHeader, claims))
                .getTokenValue();
    }

    // Todo 로그인할때마다 리프레쉬 토큰이 생성됨. 리존 리프레쉬 토큰 만료 설정하기
    public String createRefreshToken(Member member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(member.getId().toString())
                .claim("token_type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenProperties.refreshTokenExpiresDays(), ChronoUnit.DAYS))
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims))
                .getTokenValue();
    }

    public String hashRefreshToken(String refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    // 서명 검증된 JWT라도 sub가 양수인 회원 ID인지 확인합니다.
    public Long extractMemberId(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
        }
        try {
            long memberId = Long.parseLong(Objects.requireNonNull(jwt.getSubject()));
            if (memberId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
            }
            return memberId;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
        }
    }

}
