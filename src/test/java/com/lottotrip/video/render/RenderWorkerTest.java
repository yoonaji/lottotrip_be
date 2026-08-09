package com.lottotrip.video.render;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lottotrip.video.render.RenderJobStore.RenderJobSnapshot;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderWorkerTest {

    private static final String JOB_ID = "render_test1";

    private final RenderJobStore renderJobStore = mock(RenderJobStore.class);
    private final ClipStorage clipStorage = mock(ClipStorage.class);
    private final NarrationSynthesizer narrationSynthesizer = mock(NarrationSynthesizer.class);
    private final VideoMerger videoMerger = mock(VideoMerger.class);
    private final RenderWorker renderWorker =
            new RenderWorker(renderJobStore, clipStorage, narrationSynthesizer, videoMerger);

    private void stubPendingJob() {
        when(renderJobStore.findPendingJobIds()).thenReturn(List.of(JOB_ID));
        when(renderJobStore.claim(JOB_ID)).thenReturn(true);
        when(renderJobStore.loadSnapshot(JOB_ID))
                .thenReturn(new RenderJobSnapshot(List.of("https://bucket.s3.amazonaws.com/clips/1/a.mp4"), "대사"));
    }

    @Test
    void 정상_처리시_단계별_progress_갱신_후_COMPLETED로_마킹한다() throws Exception {
        stubPendingJob();
        when(clipStorage.download(any())).thenReturn(Path.of("/tmp/fake-clip.mp4"));
        when(narrationSynthesizer.synthesize(any())).thenReturn(Path.of("/tmp/fake-narration.mp3"));
        when(videoMerger.merge(any(), any())).thenReturn(Path.of("/tmp/fake-final.mp4"));
        when(clipStorage.uploadFinalVideo(any(), eq(JOB_ID)))
                .thenReturn("https://bucket.s3.amazonaws.com/renders/render_test1.mp4");

        renderWorker.pollPendingJobs();

        verify(renderJobStore).updateProgress(JOB_ID, 25);
        verify(renderJobStore).updateProgress(JOB_ID, 50);
        verify(renderJobStore).updateProgress(JOB_ID, 75);
        verify(renderJobStore).markCompleted(JOB_ID, "https://bucket.s3.amazonaws.com/renders/render_test1.mp4");
    }

    @Test
    void claim에_실패하면_처리하지_않는다() throws Exception {
        when(renderJobStore.findPendingJobIds()).thenReturn(List.of(JOB_ID));
        when(renderJobStore.claim(JOB_ID)).thenReturn(false);

        renderWorker.pollPendingJobs();

        verify(renderJobStore, never()).loadSnapshot(any());
        verify(videoMerger, never()).merge(any(), any());
    }

    @Test
    void TTS_합성_실패시_FAILED와_해당_사유코드로_저장된다() throws Exception {
        stubPendingJob();
        when(clipStorage.download(any())).thenReturn(Path.of("/tmp/fake-clip.mp4"));
        when(narrationSynthesizer.synthesize(any())).thenThrow(new RuntimeException("Polly timeout"));

        renderWorker.pollPendingJobs();

        verify(renderJobStore).markFailed(JOB_ID, "TTS_SYNTHESIS_FAILED");
    }

    @Test
    void ffmpeg_병합_실패시_FAILED와_해당_사유코드로_저장된다() throws Exception {
        stubPendingJob();
        when(clipStorage.download(any())).thenReturn(Path.of("/tmp/fake-clip.mp4"));
        when(narrationSynthesizer.synthesize(any())).thenReturn(Path.of("/tmp/fake-narration.mp3"));
        when(videoMerger.merge(any(), any())).thenThrow(new RuntimeException("ffmpeg exit 1"));

        renderWorker.pollPendingJobs();

        verify(renderJobStore).markFailed(JOB_ID, "FFMPEG_MERGE_FAILED");
    }
}
