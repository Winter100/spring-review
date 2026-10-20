package com.project.review.storage.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ImageUploadBatchRequest(
        @NotEmpty(message = "업로드할 이미지를 전달해 주세요.")
        @Size(max = 5, message = "한 번에 최대 5개까지 발급할 수 있습니다.")
        @Valid List<@NotNull ImageUploadRequest> images
) {
}
