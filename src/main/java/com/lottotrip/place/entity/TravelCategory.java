package com.lottotrip.place.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 장소 분류. ERD의 {@code travel_category} enum에 대응한다.
 *
 * <p>⚠️ 잠정값이다. ERD에 값 목록이 없어 우선 정의했고, 5단계에서 TourAPI 실제 데이터를
 * 적재하면서 조정할 수 있다.
 *
 * <p>{@code displayName}을 따로 두는 이유: DB에는 {@code BEACH}로 저장하지만 API 응답에는
 * {@code "해변"}으로 내려가야 한다. (tour_api_erd.md 4-3 응답 예시)
 * 한글을 그대로 enum 이름으로 쓰면 DB 값이 바뀔 때마다 코드가 흔들린다.
 */
@Getter
@AllArgsConstructor
public enum TravelCategory {

    NATURE("자연"),
    BEACH("해변"),
    CULTURE("문화시설"),
    HISTORY("역사"),
    LEISURE("레포츠"),
    SHOPPING("쇼핑"),
    FOOD("음식"),
    CAFE("카페");

    private final String displayName;
}
