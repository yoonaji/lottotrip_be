package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * clipUrl은 VideoUploadService#buildClipUrl과 동일한 "https://{bucket}.s3.amazonaws.com/{key}" 형식을 전제한다.
 * 버킷이 비공개라 서버가 직접 S3Client로 내려받아야 하고, 단순 HTTP GET으로는 접근 불가하다.
 */
@Component
public class S3ClipStorage implements ClipStorage {

    private final S3Client s3Client;
    private final String bucket;

    public S3ClipStorage(S3Client s3Client, @Value("${app.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public Path download(String clipUrl) throws IOException {
        String key = extractKey(clipUrl);
        ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build());

        Path tempFile = Files.createTempFile("clip-", "-" + Paths.get(key).getFileName());
        Files.write(tempFile, object.asByteArray());
        return tempFile;
    }

    @Override
    public String uploadFinalVideo(Path localFile, String jobId) throws IOException {
        String key = "renders/%s.mp4".formatted(jobId);
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("video/mp4").build(),
                RequestBody.fromFile(localFile)
        );
        return "https://%s.s3.amazonaws.com/%s".formatted(bucket, key);
    }

    private String extractKey(String clipUrl) {
        String prefix = "https://%s.s3.amazonaws.com/".formatted(bucket);
        if (!clipUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("알 수 없는 clipUrl 형식: " + clipUrl);
        }
        return clipUrl.substring(prefix.length());
    }
}
