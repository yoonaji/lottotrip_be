package com.lottotrip.video.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.video.dto.RenderRequest;
import com.lottotrip.video.dto.RenderResponse;
import com.lottotrip.video.dto.RenderStatusResponse;
import com.lottotrip.video.entity.ShortformClip;
import com.lottotrip.video.entity.ShortformJob;
import com.lottotrip.video.repository.ShortformJobRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoRenderService {

    private static final String DEFAULT_NARRATION_TYPE = "sanshilling";
    private static final String JOB_ID_PREFIX = "render_";

    private final ShortformJobRepository shortformJobRepository;

    public VideoRenderService(ShortformJobRepository shortformJobRepository) {
        this.shortformJobRepository = shortformJobRepository;
    }

    @Transactional
    public RenderResponse createJob(Long userId, RenderRequest request) {
        String narrationType = StringUtils.hasText(request.narrationType()) ? request.narrationType() : DEFAULT_NARRATION_TYPE;

        ShortformJob job = ShortformJob.create(generateJobId(), userId, request.ttsScript(), narrationType);
        request.clips().forEach(clip -> job.addClip(ShortformClip.of(clip.clipUrl(), clip.order(), clip.caption())));

        shortformJobRepository.save(job);

        return new RenderResponse(job.getJobId(), job.getStatus().name());
    }

    @Transactional(readOnly = true)
    public RenderStatusResponse getStatus(Long userId, String jobId) {
        ShortformJob job = shortformJobRepository.findById(jobId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_NOT_FOUND));

        return new RenderStatusResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getProgress(),
                job.getVideoUrl(),
                job.getFailReason()
        );
    }

    private String generateJobId() {
        return JOB_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
