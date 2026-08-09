package com.lottotrip.video.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * shortforms 테이블. jobId는 DB 시퀀스가 아니라 애플리케이션에서 생성해서 넣는다 (render_xxxxxx 형태).
 */
@Entity
@Table(name = "shortforms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortformJob {

    @Id
    @Column(name = "job_id", length = 100)
    private String jobId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "video_url")
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "tts_script", nullable = false, columnDefinition = "TEXT")
    private String ttsScript;

    @Column(name = "narration_type", length = 50)
    private String narrationType;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("playOrder ASC")
    private List<ShortformClip> clips = new ArrayList<>();

    private ShortformJob(String jobId, Long userId, String ttsScript, String narrationType) {
        this.jobId = jobId;
        this.userId = userId;
        this.ttsScript = ttsScript;
        this.narrationType = narrationType;
        this.status = JobStatus.PENDING;
        this.progress = 0;
    }

    public static ShortformJob create(String jobId, Long userId, String ttsScript, String narrationType) {
        return new ShortformJob(jobId, userId, ttsScript, narrationType);
    }

    public void addClip(ShortformClip clip) {
        clips.add(clip);
        clip.assignJob(this);
    }

    public void markProcessing() {
        this.status = JobStatus.PROCESSING;
    }

    public void updateProgress(int progress) {
        this.progress = progress;
    }

    public void markCompleted(String videoUrl) {
        this.status = JobStatus.COMPLETED;
        this.videoUrl = videoUrl;
        this.progress = 100;
    }

    public void markFailed(String failReason) {
        this.status = JobStatus.FAILED;
        this.failReason = failReason;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
