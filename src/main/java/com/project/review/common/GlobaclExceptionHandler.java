package com.project.review.common;

import com.project.review.common.dtos.CustomErrorResponse;
import com.project.review.common.exception.BusinessException;
import com.project.review.member.exception.DuplicateMemberException;
import com.project.review.member.exception.MemberNotFoundException;
import com.project.review.review.exception.ReviewNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Locale;

@Slf4j
@RestControllerAdvice
public class GlobaclExceptionHandler {

    private record FieldErrorResponse(
            String field,
            String message
    ) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<FieldErrorResponse>> handleNotValid(
            MethodArgumentNotValidException ex
    ) {
        List<FieldErrorResponse> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity.status(ex.getStatusCode())
                .body(errors);
    }

    @ExceptionHandler(BusinessException.class) // 모든 BusinessException 처리
    public ResponseEntity<CustomErrorResponse> handleBusinessException(
            BusinessException ex // 발생한 예외
    ) {
        return error(ex.getErrorCode().getStatus(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CustomErrorResponse> handleArgument(
            IllegalArgumentException ex
    ) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<CustomErrorResponse> handleBadCredentials(
            BadCredentialsException ex
    ) {
        return error(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 틀렸습니다.");
    }

    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<CustomErrorResponse> handleDuplicateMember(DuplicateMemberException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "요청 본문이 없거나 JSON 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<CustomErrorResponse> handleReviewNotFound(
            ReviewNotFoundException ex
    ) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CustomErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "요청 파라미터 '" + ex.getName() + "'의 형식이 올바르지 않습니다."
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CustomErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = normalizeConstraintName(violation.getConstraintName());
                if ("uk_members_email".equals(constraint)) {
                    return error(HttpStatus.CONFLICT, "이미 가입된 이메일 입니다.");
                }
                if ("uk_members_nickname".equals(constraint)) {
                    return error(HttpStatus.CONFLICT, "이미 생성된 닉네임 입니다.");
                }
            }
        }

        log.error("회원 중복으로 분류할 수 없는 DB 무결성 오류가 발생했습니다.", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    @ExceptionHandler(MemberNotFoundException.class)
    private ResponseEntity<CustomErrorResponse> handleMemberNotFound(
            MemberNotFoundException ex
    ) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }


    private String normalizeConstraintName(String constraintName) {
        if (constraintName == null || constraintName.isBlank()) {
            return "";
        }
        String name = constraintName.strip().split("\\s+", 2)[0];
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private ResponseEntity<CustomErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new CustomErrorResponse(message));
    }

}
