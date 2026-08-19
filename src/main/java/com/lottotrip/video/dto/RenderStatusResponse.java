package com.lottotrip.video.dto;

public record RenderStatusResponse(
        String jobId,
        String status,
        int progress,
        String videoUrl,
        String failReason
) {
}
