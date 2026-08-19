package com.lottotrip.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.video.dto.UploadUrlRequest;
import com.lottotrip.video.dto.UploadUrlResponse;
import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class VideoUploadServiceTest {

    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final VideoUploadService videoUploadService = new VideoUploadService(s3Presigner, "lottotrip-test-bucket");

    @Test
    void 요청한_fileCount만큼_presigned_URL을_발급한다() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URI("https://s3.example.com/signed").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        UploadUrlResponse response = videoUploadService.issueUploadUrls(1L, new UploadUrlRequest(2, "video/mp4"));

        assertThat(response.uploads()).hasSize(2);
        assertThat(response.uploads().get(0).order()).isEqualTo(1);
        assertThat(response.uploads().get(1).order()).isEqualTo(2);
        assertThat(response.uploads().get(0).clipUrl()).startsWith("https://lottotrip-test-bucket.s3.amazonaws.com/clips/1/");
        assertThat(response.uploads().get(0).clipUrl()).endsWith(".mp4");
    }

    @Test
    void contentType이_비어있으면_기본값_video_mp4를_사용한다() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URI("https://s3.example.com/signed").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        UploadUrlResponse response = videoUploadService.issueUploadUrls(1L, new UploadUrlRequest(1, null));

        assertThat(response.uploads().get(0).clipUrl()).endsWith(".mp4");
    }

    @Test
    void fileCount이_0이면_INVALID_FILE_COUNT_예외() {
        assertThatThrownBy(() -> videoUploadService.issueUploadUrls(1L, new UploadUrlRequest(0, null)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void fileCount이_10을_초과하면_INVALID_FILE_COUNT_예외() {
        assertThatThrownBy(() -> videoUploadService.issueUploadUrls(1L, new UploadUrlRequest(11, null)))
                .isInstanceOf(CustomException.class);
    }
}
