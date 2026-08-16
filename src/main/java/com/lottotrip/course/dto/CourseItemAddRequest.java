package com.lottotrip.course.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 코스에 담기 요청. (tour_api_erd.md 4-4)
 *
 * `placeId`가 아니라 `slotId`를 받는다. 장소 번호를 그대로 받으면
 * 뽑지도 않은 장소를 담을 수 있다.
 *
 * @param slotId 코스에 담을 슬롯 번호
 */
public record CourseItemAddRequest(@NotNull Long slotId) {
}
