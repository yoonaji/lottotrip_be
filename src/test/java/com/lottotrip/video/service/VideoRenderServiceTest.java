package com.lottotrip.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lottotrip.common.error.ApiException;
import com.lottotrip.video.dto.RenderRequest;
import com.lottotrip.video.dto.RenderRequest.ClipItem;
import com.lottotrip.video.dto.RenderResponse;
import com.lottotrip.video.dto.RenderStatusResponse;
import com.lottotrip.video.entity.JobStatus;
import com.lottotrip.video.entity.ShortformJob;
import com.lottotrip.video.repository.ShortformJobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VideoRenderServiceTest {

    private final ShortformJobRepository shortformJobRepository = mock(ShortformJobRepository.class);
    private final VideoRenderService videoRenderService = new VideoRenderService(shortformJobRepository);

    @Test
    void 릴스_생성_요청시_PENDING_상태의_job을_만든다() {
        RenderRequest request = new RenderRequest(
                List.of(new ClipItem("https://s3.../clip1.mp4", 1, "산신령이 점지해 준"), new ClipItem("https://s3.../clip2.mp4", 2, null)),
                "산신령이 점지해 준 동쪽 바다로 가거라",
                null
        );
        when(shortformJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RenderResponse response = videoRenderService.createJob(1L, request);

        assertThat(response.jobId()).startsWith("render_");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void 상태_조회시_본인_job이면_정상_반환한다() {
        ShortformJob job = ShortformJob.create("render_abc123", 1L, "대사", "sanshilling");
        when(shortformJobRepository.findById("render_abc123")).thenReturn(Optional.of(job));

        RenderStatusResponse response = videoRenderService.getStatus(1L, "render_abc123");

        assertThat(response.jobId()).isEqualTo("render_abc123");
        assertThat(response.status()).isEqualTo(JobStatus.PENDING.name());
        assertThat(response.progress()).isZero();
    }

    @Test
    void 존재하지_않는_job_조회시_예외() {
        when(shortformJobRepository.findById("render_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> videoRenderService.getStatus(1L, "render_missing"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 다른_유저의_job_조회시_예외() {
        ShortformJob job = ShortformJob.create("render_abc123", 1L, "대사", "sanshilling");
        when(shortformJobRepository.findById("render_abc123")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> videoRenderService.getStatus(2L, "render_abc123"))
                .isInstanceOf(ApiException.class);
    }
}
