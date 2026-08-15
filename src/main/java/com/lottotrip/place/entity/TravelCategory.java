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
    CAFE("카페"),

    // ---------- 아래 3종은 결정 13(전 종류 포괄)으로 추가됐다 (roadmap 6-10) ----------

    /**
     * 숙박(관광타입 32).
     *
     * <p>배치 시절에는 적재 대상이 아니라 대응 값이 필요 없었다. 온디맨드로 바뀌며 전 종류를 담게 되어
     * <b>실제로 들어온다.</b> 없으면 기본값으로 떨어져 <b>모텔이 "자연"으로 저장된다.</b>
     */
    LODGING("숙박"),

    /** 축제·공연·행사(관광타입 15). 강원 기준 24건으로 적지만 분류가 없으면 역시 기본값으로 뭉친다. */
    FESTIVAL("축제"),

    /** 여행코스(관광타입 25). 장소 하나가 아니라 묶음이라 성격이 다르지만, 전 종류를 담는 이상 자리가 필요하다. */
    COURSE("여행코스");

    private final String displayName;
}
