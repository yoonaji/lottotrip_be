package com.lottotrip.video.controller;

import com.lottotrip.common.auth.CurrentUserId;
import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.video.dto.RenderRequest;
import com.lottotrip.video.dto.RenderResponse;
import com.lottotrip.video.dto.RenderStatusResponse;
import com.lottotrip.video.dto.UploadUrlRequest;
import com.lottotrip.video.dto.UploadUrlResponse;
import com.lottotrip.video.service.VideoRenderService;
import com.lottotrip.video.service.VideoUploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/video")
public class VideoController {

    private final VideoUploadService videoUploadService;
    private final VideoRenderService videoRenderService;

    public VideoController(VideoUploadService videoUploadService, VideoRenderService videoRenderService) {
        this.videoUploadService = videoUploadService;
        this.videoRenderService = videoRenderService;
    }

    @PostMapping("/upload-urls")
    public ApiResponse<UploadUrlResponse> issueUploadUrls(
            @CurrentUserId Long userId,
            @Valid @RequestBody UploadUrlRequest request
    ) {
        UploadUrlResponse response = videoUploadService.issueUploadUrls(userId, request);
        return ApiResponse.success(HttpStatus.OK.value(), response);
    }

    @PostMapping("/render")
    public ApiResponse<RenderResponse> createRenderJob(
            @CurrentUserId Long userId,
            @Valid @RequestBody RenderRequest request
    ) {
        RenderResponse response = videoRenderService.createJob(userId, request);
        return ApiResponse.success(HttpStatus.OK.value(), response);
    }

    @GetMapping("/render/{jobId}")
    public ApiResponse<RenderStatusResponse> getRenderStatus(
            @CurrentUserId Long userId,
            @PathVariable String jobId
    ) {
        RenderStatusResponse response = videoRenderService.getStatus(userId, jobId);
        return ApiResponse.success(HttpStatus.OK.value(), response);
    }
}
