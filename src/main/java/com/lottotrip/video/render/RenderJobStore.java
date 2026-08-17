package com.lottotrip.video.render;

import com.lottotrip.video.entity.JobStatus;
import com.lottotrip.video.entity.ShortformJob;
import com.lottotrip.video.repository.ShortformJobRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RenderWorker의 트랜잭션 경계를 전담하는 별도 빈.
 * RenderWorker 안에서 this.xxx()로 직접 호출하면 프록시를 안 거쳐서 @Transactional이 조용히 무시되기 때문에
 * (Spring self-invocation 문제) 반드시 다른 빈으로 분리해야 한다.
 */
@Component
public class RenderJobStore {

    private final ShortformJobRepository shortformJobRepository;

    public RenderJobStore(ShortformJobRepository shortformJobRepository) {
        this.shortformJobRepository = shortformJobRepository;
    }

    @Transactional(readOnly = true)
    public List<String> findPendingJobIds() {
        return shortformJobRepository.findByStatus(JobStatus.PENDING).stream()
                .map(ShortformJob::getJobId)
                .toList();
    }

    @Transactional
    public boolean claim(String jobId) {
        return shortformJobRepository.findById(jobId)
                .filter(job -> job.getStatus() == JobStatus.PENDING)
                .map(job -> {
                    job.markProcessing();
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public RenderJobSnapshot loadSnapshot(String jobId) {
        ShortformJob job = shortformJobRepository.findById(jobId).orElseThrow();
        List<ClipRef> clips = job.getClips().stream()
                .map(clip -> new ClipRef(clip.getClipUrl(), clip.getCaption()))
                .toList();
        return new RenderJobSnapshot(clips, job.getTtsScript());
    }

    @Transactional
    public void updateProgress(String jobId, int progress) {
        shortformJobRepository.findById(jobId).ifPresent(job -> job.updateProgress(progress));
    }

    @Transactional
    public void markCompleted(String jobId, String videoUrl) {
        shortformJobRepository.findById(jobId).ifPresent(job -> job.markCompleted(videoUrl));
    }

    @Transactional
    public void markFailed(String jobId, String reasonCode) {
        shortformJobRepository.findById(jobId).ifPresent(job -> job.markFailed(reasonCode));
    }

    public record RenderJobSnapshot(List<ClipRef> clips, String ttsScript) {
    }

    public record ClipRef(String clipUrl, String caption) {
    }
}
