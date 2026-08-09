package com.lottotrip.video.service;

import com.lottotrip.common.error.ApiException;
import com.lottotrip.common.error.ErrorCode;
import com.lottotrip.video.dto.UploadUrlRequest;
import com.lottotrip.video.dto.UploadUrlResponse;
import com.lottotrip.video.dto.UploadUrlResponse.UploadItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class VideoUploadService {

    private static final int MIN_FILE_COUNT = 1;
    private static final int MAX_FILE_COUNT = 10;
    private static final String DEFAULT_CONTENT_TYPE = "video/mp4";
    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);

    private final S3Presigner s3Presigner;
    private final String bucket;

    public VideoUploadService(S3Presigner s3Presigner, @Value("${app.s3.bucket}") String bucket) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    public UploadUrlResponse issueUploadUrls(Long userId, UploadUrlRequest request) {
        int fileCount = request.fileCount();
        if (fileCount < MIN_FILE_COUNT || fileCount > MAX_FILE_COUNT) {
            throw new ApiException(ErrorCode.INVALID_FILE_COUNT);
        }
        String contentType = StringUtils.hasText(request.contentType()) ? request.contentType() : DEFAULT_CONTENT_TYPE;

        List<UploadItem> uploads = new ArrayList<>(fileCount);
        for (int order = 1; order <= fileCount; order++) {
            uploads.add(presignOne(userId, order, contentType));
        }
        return new UploadUrlResponse(uploads);
    }

    private UploadItem presignOne(Long userId, int order, String contentType) {
        String key = buildKey(userId, contentType);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new UploadItem(order, presigned.url().toString(), buildClipUrl(key), PRESIGN_DURATION.toSeconds());
    }

    private String buildKey(Long userId, String contentType) {
        String extension = contentType.substring(contentType.indexOf('/') + 1);
        return "clips/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);
    }

    private String buildClipUrl(String key) {
        return "https://%s.s3.amazonaws.com/%s".formatted(bucket, key);
    }
}
