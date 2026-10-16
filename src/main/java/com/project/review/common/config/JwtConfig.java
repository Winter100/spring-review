package com.project.review.common.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtKey(
            @Value("${security.jwt.secret-base64}")
            String secret
    ){
        byte[] key = Base64.getDecoder().decode(secret);

        if(key.length < 32) {
            throw new IllegalArgumentException("JWT 키는 32바이트 이상이어야 합니다.");
        }

        return new SecretKeySpec(key, "HmacsSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(
            SecretKey jwtKey
    ){
        // Security가 제공하는 JWT 구현체
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    public JwtDecoder jwtDecoder (
            SecretKey jwtKey,
            @Value("${security.jwt.issuer}")
            String issuer
    ){
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // 리프레시 토큰을 Bearer 액세스 토큰으로 사용하는 요청은 거절합니다.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> "refresh".equals(jwt.getClaimAsString("token_use"))
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "리프레시 토큰은 API 인증에 사용할 수 없습니다.", null))
                        : OAuth2TokenValidatorResult.success()
        ));

        return decoder;
    }
}
