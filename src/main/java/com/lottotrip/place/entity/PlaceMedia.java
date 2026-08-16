package com.lottotrip.place.entity;

import com.lottotrip.common.enums.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * 장소 이미지. (tour_api_erd.md 1 — place_media)
 *
 * `media_url`이 슬롯 응답의 `thumbnailUrl`로 나간다.
 * 한 장소에 여러 장이 붙을 수 있어 별도 테이블로 분리돼 있다.
 */
@Entity
@Table(name = "place_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "media_url", nullable = false, columnDefinition = "TEXT")
    private String mediaUrl;

    /** ERD 컬럼명은 `m_type`이다. 필드명은 뜻이 드러나도록 mediaType으로 둔다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "m_type", nullable = false, length = 20)
    private MediaType mediaType;

    private PlaceMedia(Place place, String mediaUrl, MediaType mediaType) {
        this.place = place;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
    }

    public static PlaceMedia create(Place place, String mediaUrl, MediaType mediaType) {
        return new PlaceMedia(place, mediaUrl, mediaType);
    }
}
