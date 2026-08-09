package com.lottotrip.video.dto;

import jakarta.validation.constraints.NotNull;

public record UploadUrlRequest(
        @NotNull Integer fileCount,
        String contentType
) {
}
