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

    private ShortformClip(String clipUrl, int playOrder) {
        this.clipUrl = clipUrl;
        this.playOrder = playOrder;
    }

    public static ShortformClip of(String clipUrl, int playOrder) {
        return new ShortformClip(clipUrl, playOrder);
    }

    void assignJob(ShortformJob job) {
        this.job = job;
    }
}
