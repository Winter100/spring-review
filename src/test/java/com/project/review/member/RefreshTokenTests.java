package com.project.review.member;

import com.project.review.member.entities.Member;
import com.project.review.member.entities.RefreshToken;
import com.project.review.member.repository.MemberRepository;
import com.project.review.member.repository.RefreshTokenRepository;
import com.project.review.member.service.TokenService;
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

        String body = mvc.perform(post("/api/v1/member/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
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
        expectRejected(refreshToken, "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요.");
        assertThat(tokens.count()).isZero();
    }

    @Test
    void missingCookieReturns401AndClearsCookie() throws Exception {
        mvc.perform(post("/api/v1/member/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("리프레시 토큰이 없습니다. 다시 로그인해 주세요."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void blankCookieReturns401() throws Exception {
        expectRejected("", "리프레시 토큰이 없습니다. 다시 로그인해 주세요.");
    }

    @Test
    void unregisteredSignedRefreshTokenReturns401() throws Exception {
        expectRejected(tokenService.createRefreshToken(member),
                "유효하지 않은 리프레시 토큰입니다. 다시 로그인해 주세요.");
    }

    @Test
    void tamperedTokenReturns401WithoutDeletingValidToken() throws Exception {
        String refreshToken = saveToken(Instant.now().plusSeconds(3600));
        expectRejected(refreshToken + "tampered",
                "유효하지 않은 리프레시 토큰입니다. 다시 로그인해 주세요.");
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
        expectRejected(tokenService.createAccessToken(member),
                "유효하지 않은 리프레시 토큰입니다. 다시 로그인해 주세요.");
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
    void secureRequestClearsCookieWithMatchingAttributes() throws Exception {
        mvc.perform(post("/api/v1/member/refresh").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1")));
    }

    @Test
    void loginCookieCanBeUsedForRefresh() throws Exception {
        var login = mvc.perform(post("/api/v1/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.member.id").value(member.getId()))
                .andExpect(jsonPath("$.member.email").value(member.getEmail()))
                .andExpect(jsonPath("$.member.password").doesNotExist())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn().getResponse();

        Cookie refreshCookie = login.getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getMaxAge()).isEqualTo(7 * 24 * 60 * 60);
        mvc.perform(post("/api/v1/member/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void signupHashesPasswordAndAssignsUserRole() throws Exception {
        mvc.perform(post("/api/v1/member")
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
        mvc.perform(post("/api/v1/member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@example.com","password":"password123","nickname":"new-user"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"password123","nickname":"refresh-user"}
                                """))
                .andExpect(status().isConflict());

        assertThat(members.count()).isEqualTo(1);
    }

    @Test
    void wrongPasswordDoesNotIssueTokens() throws Exception {
        mvc.perform(post("/api/v1/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(tokens.count()).isZero();
    }

    @Test
    void repeatedLoginsStoreDistinctHashesInsteadOfRawTokens() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            Cookie cookie = mvc.perform(post("/api/v1/member/login")
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
        assertThat(tokens.count()).isEqualTo(2);
    }

    private String saveToken(Instant expiresAt) {
        String token = tokenService.createRefreshToken(member);
        tokens.save(new RefreshToken(member, tokenService.hashRefreshToken(token), expiresAt));
        return token;
    }

    private void expectRejected(String token, String message) throws Exception {
        mvc.perform(post("/api/v1/member/refresh").cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
}
