package com.lottotrip.place.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 장소 이미지 항목. (roadmap 5-1)
 *
 * <p>{@code place_media.media_url}에 들어갈 값이다. 슬롯 응답의 {@code thumbnailUrl}이 여기서 나온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiImageItem(
        @JsonProperty("contentid") String contentId,
        @JsonProperty("originimgurl") String originImgUrl,
        @JsonProperty("smallimageurl") String smallImageUrl,
        @JsonProperty("imgname") String imgName
) {
}
