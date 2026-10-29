package com.project.review.common.exception;

import com.project.review.auth.entities.RefreshToken;
import com.project.review.review.entities.Review;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerOptimisticLockTests {

    private final FailureController controller = new FailureController();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @MethodSource("tokenConflicts")
    void tokenConflictPreservesCookieAndReturnsAuthenticationMessage(RuntimeException failure) throws Exception {
        controller.failure = failure;

        mvc.perform(get("/failure"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "인증 정보가 다른 요청에 의해 변경되었습니다. 최신 인증 상태를 확인해 주세요."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private static Stream<RuntimeException> tokenConflicts() {
        RefreshToken token = new RefreshToken(null, "hash", null);
        return Stream.of(
                new ObjectOptimisticLockingFailureException(RefreshToken.class, 1L),
                new ObjectOptimisticLockingFailureException(RefreshToken.class.getName(), 1L),
                new OptimisticLockException("conflict", null, token),
                new OptimisticLockingFailureException("commit failed",
                        new OptimisticLockException("conflict", null, token)),
                new OptimisticLockException("commit failed",
                        new StaleObjectStateException(RefreshToken.class.getName(), 1L))
        );
    }

    @ParameterizedTest
    @MethodSource("reviewConflicts")
    void reviewConflictKeepsExistingResponse(RuntimeException failure) throws Exception {
        controller.failure = failure;

        mvc.perform(get("/failure"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.REVIEW_UPDATE_CONFLICT.getMessage()))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private static Stream<RuntimeException> reviewConflicts() {
        return Stream.of(
                new ObjectOptimisticLockingFailureException(Review.class, 1L),
                new OptimisticLockException(),
                new OptimisticLockingFailureException("conflict")
        );
    }

    @RestController
    static class FailureController {
        private RuntimeException failure;

        @GetMapping("/failure")
        void fail() {
            throw failure;
        }
    }
}
