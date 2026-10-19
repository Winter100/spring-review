package com.project.review.storage;

import com.project.review.common.exception.GlobaclExceptionHandler;
import com.project.review.member.service.TokenService;
import com.project.review.storage.controller.StorageController;
import com.project.review.storage.dtos.ImageUploadRequest;
import com.project.review.storage.dtos.ImageUploadResponse;
import com.project.review.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StorageUploadValidationTests {

    private StorageService storageService;
    private TokenService tokenService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        tokenService = mock(TokenService.class);
        when(tokenService.extractMemberId(null)).thenReturn(1L);
        mvc = MockMvcBuilders.standaloneSetup(new StorageController(storageService, tokenService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobaclExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1, 0, 5_242_881})
    void rejectsInvalidFileSizeBeforeIssuingUrl(Long fileSize) throws Exception {
        String body = fileSize == null
                ? "{\"contentType\":\"image/png\"}"
                : "{\"contentType\":\"image/png\",\"fileSize\":%d}".formatted(fileSize);

        mvc.perform(post("/api/v1/storage/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(storageService);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 5_242_880})
    void acceptsFileSizeWithinLimit(long fileSize) throws Exception {
        when(storageService.uploadUrl(new ImageUploadRequest("image/png", fileSize), 1L))
                .thenReturn(new ImageUploadResponse("https://example.com/upload", "reviews/test"));

        mvc.perform(post("/api/v1/storage/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"fileSize\":%d}".formatted(fileSize)))
                .andExpect(status().isOk());

        verify(storageService).uploadUrl(new ImageUploadRequest("image/png", fileSize), 1L);
    }
}
