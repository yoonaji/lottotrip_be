package com.lottotrip.video.render;

import com.lottotrip.video.render.RenderJobStore.RenderJobSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PENDING job을 주기적으로 폴링해서 처리하는 단일 인스턴스용 워커.
 * 트랜잭션 경계는 전부 RenderJobStore에 위임한다 (self-invocation 때문에 이 클래스 안에 두면 안 됨).
 * 여러 인스턴스로 스케일아웃하면 claim()의 read-then-write에 경쟁 조건이 생길 수 있음 —
 * 지금은 단일 서버 전제라 SELECT FOR UPDATE 같은 잠금은 넣지 않았다. SQS+Lambda로 옮길 때 이 클래스 전체가 대체 대상.
 */
@Slf4j
@Component
public class RenderWorker {

    private static final long POLL_INTERVAL_MS = 5_000;

    private final RenderJobStore renderJobStore;
    private final ClipStorage clipStorage;
    private final NarrationSynthesizer narrationSynthesizer;
    private final VideoMerger videoMerger;

    public RenderWorker(RenderJobStore renderJobStore, ClipStorage clipStorage,
                         NarrationSynthesizer narrationSynthesizer, VideoMerger videoMerger) {
        this.renderJobStore = renderJobStore;
        this.clipStorage = clipStorage;
        this.narrationSynthesizer = narrationSynthesizer;
        this.videoMerger = videoMerger;
    }

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void pollPendingJobs() {
        renderJobStore.findPendingJobIds().forEach(this::processIfClaimed);
    }

    private void processIfClaimed(String jobId) {
        if (!renderJobStore.claim(jobId)) {
            return;
        }
        try {
            process(jobId);
        } catch (RenderStageException e) {
            log.error("숏폼 렌더링 실패: jobId={}, reason={}", jobId, e.getReasonCode(), e.getCause());
            renderJobStore.markFailed(jobId, e.getReasonCode());
        } catch (Exception e) {
            log.error("숏폼 렌더링 실패(알 수 없는 원인): jobId={}", jobId, e);
            renderJobStore.markFailed(jobId, "UNKNOWN_ERROR");
        }
    }

    private void process(String jobId) throws Exception {
        RenderJobSnapshot snapshot = renderJobStore.loadSnapshot(jobId);
        List<Path> tempFiles = new ArrayList<>();
        try {
            List<Path> clipFiles = downloadClips(snapshot.clipUrls(), tempFiles);
            renderJobStore.updateProgress(jobId, 25);

            Path narrationAudio = synthesizeNarration(snapshot.ttsScript(), tempFiles);
            renderJobStore.updateProgress(jobId, 50);

            Path finalVideo = mergeVideo(clipFiles, narrationAudio, tempFiles);
            renderJobStore.updateProgress(jobId, 75);

            String videoUrl = uploadFinalVideo(finalVideo, jobId);
            renderJobStore.markCompleted(jobId, videoUrl);
        } finally {
            cleanup(tempFiles);
        }
    }

    private List<Path> downloadClips(List<String> clipUrls, List<Path> tempFiles) {
        try {
            List<Path> clipFiles = new ArrayList<>();
            for (String clipUrl : clipUrls) {
                Path file = clipStorage.download(clipUrl);
                clipFiles.add(file);
                tempFiles.add(file);
            }
            return clipFiles;
        } catch (Exception e) {
            throw new RenderStageException("CLIP_DOWNLOAD_FAILED", e);
        }
    }

    private Path synthesizeNarration(String ttsScript, List<Path> tempFiles) {
        try {
            Path narrationAudio = narrationSynthesizer.synthesize(ttsScript);
            tempFiles.add(narrationAudio);
            return narrationAudio;
        } catch (Exception e) {
            throw new RenderStageException("TTS_SYNTHESIS_FAILED", e);
        }
    }

    private Path mergeVideo(List<Path> clipFiles, Path narrationAudio, List<Path> tempFiles) {
        try {
            Path finalVideo = videoMerger.merge(clipFiles, narrationAudio);
            tempFiles.add(finalVideo);
            return finalVideo;
        } catch (Exception e) {
            throw new RenderStageException("FFMPEG_MERGE_FAILED", e);
        }
    }

    private String uploadFinalVideo(Path finalVideo, String jobId) {
        try {
            return clipStorage.uploadFinalVideo(finalVideo, jobId);
        } catch (Exception e) {
            throw new RenderStageException("S3_UPLOAD_FAILED", e);
        }
    }

    private void cleanup(List<Path> tempFiles) {
        for (Path path : tempFiles) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("임시 파일 삭제 실패: {}", path, e);
            }
        }
    }
}
