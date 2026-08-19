package com.lottotrip.video.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shortform_clips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortformClip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clip_id")
    private Long clipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ShortformJob job;

    @Column(name = "clip_url", nullable = false)
    private String clipUrl;

    @Column(name = "play_order", nullable = false)
    private int playOrder;

    /** 이 클립이 재생되는 동안 화면에 번인되는 짧은 자막. 없으면 자막 없이 재생. */
    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    private ShortformClip(String clipUrl, int playOrder, String caption) {
        this.clipUrl = clipUrl;
        this.playOrder = playOrder;
        this.caption = caption;
    }

    public static ShortformClip of(String clipUrl, int playOrder, String caption) {
        return new ShortformClip(clipUrl, playOrder, caption);
    }

    void assignJob(ShortformJob job) {
        this.job = job;
    }
}
