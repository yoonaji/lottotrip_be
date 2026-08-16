package com.lottotrip.place.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 지역 코드 항목. (roadmap 5-1, 5-3에서 사용)
 *
 * 시도(states)와 시군구(cities)가 같은 모양으로 온다. 요청에 지역 코드를 주면 그 시도의
 * 시군구 목록이, 주지 않으면 시도 목록이 온다.
 *
 * @param rnum 응답 안에서의 일련번호. 우리가 쓰지는 않지만 받아 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiAreaCodeItem(
        String code,
        String name,
        Integer rnum
) {
}
