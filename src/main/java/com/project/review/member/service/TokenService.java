package com.project.review.member.service;

import com.project.review.common.config.TokenProperties;
import com.project.review.member.entities.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
@RequiredArgsConstructor
public class TokenService {

    @Value("${security.jwt.issuer}")
    private String issuer;
    private final JwtEncoder jwtEncoder;
    private final TokenProperties tokenProperties;

    public String createAccessToken(Member member) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(member.getId().toString())
                .claim("role", member.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenProperties.accessTokenExpiresMinutes(), ChronoUnit.MINUTES))
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(jwsHeader, claims))
                .getTokenValue();
    }

    public String createRefreshToken(Member member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(member.getId().toString())
                .claim("token_use", "refresh")
                // 같은 회원이 연속 로그인해도 서로 다른 토큰을 발급합니다.
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

}
