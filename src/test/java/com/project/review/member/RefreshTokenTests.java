package com.project.review.member;

import com.project.review.member.entities.Member;
import com.project.review.auth.entities.RefreshToken;
import com.project.review.member.repository.MemberRepository;
import com.project.review.auth.repository.RefreshTokenRepository;
import com.project.review.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 Security 필터, JWT 발급, H2 저장소와 HTTP 쿠키 응답을 함께 검증합니다. */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:refresh-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.issuer=project-review",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15",
        "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com",
        "storage.bucket=test-bucket",
        "storage.access-key=test-access-key",
        "storage.secret-key=test-secret-key",
        "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
class RefreshTokenTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private RefreshTokenRepository tokens;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member member;

    @BeforeEach
    void setUp() {
        tokens.deleteAll();
        members.deleteAll();
        member = members.save(Member.builder()
                .email("refresh@example.com")
                .nickname("refresh-user")
                .password(passwordEncoder.encode("password123"))
                .build());
    }

    @Test
    void refreshIssuesUsableAccessTokenWithoutExtendingRefreshLifetime() throws Exception {
        String refreshToken = saveToken(Instant.now().plusSeconds(3600));
        Instant originalExpiry = tokens.findAll().getFirst().getExpiresAt();

        String body = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(body).get("accessToken").asString();
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo(member.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        assertThat(tokens.findAll()).singleElement()
                .satisfies(token -> assertThat(token.getExpiresAt()).isEqualTo(originalExpiry));

        // 보호된 리뷰 API의 인증을 통과한 뒤 요청 본문 검증에서 400이 나와야 합니다.
        mvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredTokenReturns401AndCommitsDeletion() throws Exception {
        String refreshToken = saveToken(Instant.now().minusSeconds(1));
        expectRejected(refreshToken, "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
        assertThat(tokens.count()).isZero();
    }

    @Test
    void missingCookieReturns401AndClearsCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void blankCookieReturns401() throws Exception {
        expectRejected("", "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
    }

    @Test
    void unregisteredSignedRefreshTokenReturns401() throws Exception {
        expectRejected(tokenService.createRefreshToken(member),
                "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
    }

    @Test
    void tamperedTokenReturns401WithoutDeletingValidToken() throws Exception {
        String refreshToken = saveToken(Instant.now().plusSeconds(3600));
        expectRejected(refreshToken + "tampered",
                "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
        expectRejected(tokenService.createAccessToken(member),
                "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
    }

    @Test
    void refreshTokenCannotBeUsedAsBearerToken() throws Exception {
        String refreshToken = saveToken(Instant.now().plusSeconds(3600));
        mvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerRejectsMissingAndUnknownTokenTypes() throws Exception {
        for (String type : new String[]{null, "unknown"}) {
            String token = signedToken(type, Instant.now().plusSeconds(3600));
            mvc.perform(post("/api/v1/reviews")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void refreshRejectsRegisteredTokensWithWrongOrMissingType() throws Exception {
        for (String type : new String[]{"access", "unknown", null}) {
            String token = signedToken(type, Instant.now().plusSeconds(3600));
            RefreshToken stored = tokens.save(new RefreshToken(
                    member, tokenService.hashRefreshToken(token), Instant.now().plusSeconds(3600)));
            expectRejected(token, "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
            assertThat(tokens.existsById(stored.getId())).isTrue();
            tokens.delete(stored);
        }
    }

    @Test
    void expiredJwtDeletesTokenEvenWhenDatabaseExpiryIsInFuture() throws Exception {
        String token = signedToken("refresh", Instant.now().minusSeconds(1));
        tokens.save(new RefreshToken(
                member, tokenService.hashRefreshToken(token), Instant.now().plusSeconds(3600)));

        expectRejected(token, "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");

        assertThat(tokens.count()).isZero();
    }

    @Test
    void refreshRejectsTokenWithoutExpiry() throws Exception {
        String token = signedToken("refresh", null);
        tokens.save(new RefreshToken(
                member, tokenService.hashRefreshToken(token), Instant.now().plusSeconds(3600)));

        expectRejected(token, "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");

        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void refreshRejectsWrongIssuerAndFutureNotBefore() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet wrongIssuer = JwtClaimsSet.builder()
                .issuer("other-issuer")
                .subject(member.getId().toString())
                .claim("token_type", "refresh")
                .expiresAt(now.plusSeconds(3600))
                .build();
        JwtClaimsSet futureToken = JwtClaimsSet.builder()
                .issuer("project-review")
                .subject(member.getId().toString())
                .claim("token_type", "refresh")
                .notBefore(now.plusSeconds(1800))
                .expiresAt(now.plusSeconds(3600))
                .build();

        for (JwtClaimsSet claims : new JwtClaimsSet[]{wrongIssuer, futureToken}) {
            String token = encodeToken(claims);
            RefreshToken stored = tokens.save(new RefreshToken(
                    member, tokenService.hashRefreshToken(token), now.plusSeconds(3600)));
            expectRejected(token, "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
            assertThat(tokens.existsById(stored.getId())).isTrue();
            tokens.delete(stored);
        }
    }

    @Test
    void refreshRejectsInvalidSignatureEvenWhenHashIsRegistered() throws Exception {
        String token = tokenService.createRefreshToken(member);
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tamperedToken = token.substring(0, signatureStart)
                + replacement + token.substring(signatureStart + 1);
        tokens.save(new RefreshToken(
                member, tokenService.hashRefreshToken(tamperedToken), Instant.now().plusSeconds(3600)));

        expectRejected(tamperedToken, "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");

        assertThat(tokens.count()).isEqualTo(1);
    }

    private String signedToken(String type, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("project-review")
                .subject(member.getId().toString())
                .issuedAt(Instant.now().minusSeconds(60));
        if (type != null) {
            claims.claim("token_type", type);
        }
        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        return encodeToken(claims.build());
    }

    private String encodeToken(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void secureRequestClearsCookieWithMatchingAttributes() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));
    }

    @Test
    void loginCookieCanBeUsedForRefresh() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.user.id").value(member.getId()))
                .andExpect(jsonPath("$.user.email").value(member.getEmail()))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn().getResponse();

        Cookie refreshCookie = login.getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getMaxAge()).isEqualTo(7 * 24 * 60 * 60);
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void signupHashesPasswordAndAssignsUserRole() throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"password123","nickname":"new-user"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        Member created = members.findByEmail("new@example.com").orElseThrow();
        assertThat(created.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", created.getPassword())).isTrue();
        assertThat(created.getRole().name()).isEqualTo("USER");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void duplicateEmailAndNicknameAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@example.com","password":"password123","nickname":"new-user"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"password123","nickname":"refresh-user"}
                                """))
                .andExpect(status().isConflict());

        assertThat(members.count()).isEqualTo(1);
    }

    @Test
    void wrongPasswordDoesNotIssueTokens() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(tokens.count()).isZero();
    }

    @Test
    void refreshRotatesCookieAndRejectsPreviousToken() throws Exception {
        String originalToken = saveToken(Instant.now().plusSeconds(3600));
        Instant originalExpiry = tokens.findAll().getFirst().getExpiresAt();

        Cookie replacement = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", originalToken)))
                .andExpect(status().isOk())
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andReturn().getResponse().getCookie("refreshToken");

        assertThat(replacement).isNotNull();
        assertThat(replacement.getValue()).isNotEqualTo(originalToken);
        assertThat(replacement.getMaxAge()).isBetween(1, 3600);
        assertThat(tokens.findByTokenHash(tokenService.hashRefreshToken(originalToken))).isEmpty();
        assertThat(tokens.findByTokenHash(tokenService.hashRefreshToken(replacement.getValue())))
                .hasValueSatisfying(token -> assertThat(token.getExpiresAt()).isEqualTo(originalExpiry));

        mvc.perform(post("/api/v1/auth/refresh").cookie(replacement))
                .andExpect(status().isOk());
        expectRejected(originalToken, "로그인이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
    }

    @Test
    void logoutDeletesStoredTokenWithoutAccessToken() throws Exception {
        String refreshToken = saveToken(Instant.now().plusSeconds(3600));

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andExpect(cookie().maxAge("refreshToken", 0));

        assertThat(tokens.count()).isZero();
    }

    @Test
    void repeatedLoginsReplaceHashInSingleRow() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            Cookie cookie = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"refresh@example.com","password":"password123"}
                                    """))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getCookie("refreshToken");

            assertThat(cookie).isNotNull();
            assertThat(tokens.findByTokenHash(tokenService.hashRefreshToken(cookie.getValue())))
                    .isPresent();
            assertThat(tokens.findByTokenHash(cookie.getValue())).isEmpty();
        }
        assertThat(tokens.count()).isEqualTo(1);
    }

    private String saveToken(Instant expiresAt) {
        String token = tokenService.createRefreshToken(member);
        tokens.save(new RefreshToken(member, tokenService.hashRefreshToken(token), expiresAt));
        return token;
    }

    private void expectRejected(String token, String message) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
}
