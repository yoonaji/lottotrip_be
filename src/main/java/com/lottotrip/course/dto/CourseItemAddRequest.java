package com.lottotrip.course.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 코스에 담기 요청. (tour_api_erd.md 4-4)
 *
 * **`placeId`가 아니라 `slotId`를 받는다.** 장소 번호를 그대로 받으면
 * **뽑지도 않은 장소를 담을 수 있다.** 슬롯 번호로 받으면 서버가 "그 슬롯이 이 회원의 것인가"를
 * 확인할 수 있어, 자기가 실제로 뽑은 것만 담게 된다.
 *
 * @param slotId 코스에 담을 슬롯 번호
 */
public record CourseItemAddRequest(@NotNull Long slotId) {
}
