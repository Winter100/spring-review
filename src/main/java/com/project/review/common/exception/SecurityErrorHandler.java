package com.project.review.common.exception;

import com.project.review.common.dtos.CustomErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 컨트롤러에 도달하기 전에 발생한 인증 및 권한 오류를 JSON으로 전달합니다. */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final BearerTokenAuthenticationEntryPoint authenticationEntryPoint =
            new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler accessDeniedHandler =
            new BearerTokenAccessDeniedHandler();

    // 401
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // Bearer 표준 상태 코드와 WWW-Authenticate 헤더는 기본 처리기에 맡깁니다.
        authenticationEntryPoint.commence(request, response, exception);
        writeError(response, "인증이 필요하거나 토큰이 유효하지 않습니다.");
    }

    // 403
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        accessDeniedHandler.handle(request, response, exception);
        writeError(response, "접근 권한이 없습니다.");
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new CustomErrorResponse(message));
    }
}
