package com.lottotrip.place.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 공통 상세 정보 항목. (roadmap 5-1)
 *
 * <p>목록 조회에는 소개글이 없다. {@code places.description}을 채우려면 장소마다 이 조회를 한 번 더 해야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailItem(
        @JsonProperty("contentid") String contentId,
        String title,
        String overview,
        String homepage
) {
}
