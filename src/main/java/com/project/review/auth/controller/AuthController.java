package com.project.review.auth.controller;

import com.project.review.auth.dtos.*;
import com.project.review.auth.service.AuthService;
import com.project.review.member.dtos.MemberResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Auth 에서 하는 것
 * - 로그인
 * - 로그아웃
 * - 회원가입
 * - 토큰 재발급
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request
    ) {

        LoginResult result = authService.login(loginRequest);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(result.lifetime())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(LoginResponse.from(result.member(), result.accessToken()));
    }

    // 회원가입
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signup(
            @Valid @RequestBody SignupRequest signupRequest
    ) {
        return authService.signup(signupRequest);
    }


    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name="refreshToken", required = false) String refreshToken,
            HttpServletRequest request
    ){

        authService.logout(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    // 리프레쉬를 통한 토큰 재발급
    @PostMapping("refresh")
    public ResponseEntity<TokenResponse> renewToken(
            @CookieValue(name = "refreshToken" , required = false) String refreshToken,
            HttpServletRequest request
    ){
        RefreshResult result = authService.refreshAccessToken(refreshToken);

        ResponseCookie cookie = ResponseCookie
                .from("refreshToken", result.newRefreshToken())
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.between(Instant.now(), result.expiresAt()))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(result.accessToken()));
    }
}
