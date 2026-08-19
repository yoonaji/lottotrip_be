package com.lottotrip.video.dto;

import java.util.List;

public record UploadUrlResponse(List<UploadItem> uploads) {

    public record UploadItem(int order, String uploadUrl, String clipUrl, long expiresIn) {
    }
}
