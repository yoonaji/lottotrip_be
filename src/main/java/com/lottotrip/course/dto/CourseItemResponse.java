package com.lottotrip.course.dto;

import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.place.entity.Place;

import java.time.LocalDateTime;

/**
 * 코스에 담은 결과. (tour_api_erd.md 4-4)
 *
 * <p>Entity를 그대로 내보내지 않는 이유는 슬롯 응답과 같다 — {@code Place}에는 {@code contentId}처럼
 * 우리 사정에 쓰는 값이 함께 있고, 테이블 구조가 바뀔 때마다 응답이 따라 흔들린다.
 *
 * <p><b>장소 정보를 {@code placeId}·{@code name}까지만 싣는다.</b> 명세의 응답 예시가 그렇고,
 * 담긴 직후 화면에 필요한 것도 그 둘뿐이다. 자세한 정보는 코스 조회(7-3)가 준다.
 *
 * @param addedAt 담은 시각. {@code course_items.added_at}이 그대로 나간다
 */
public record CourseItemResponse(Long itemId, PlaceInfo place, LocalDateTime addedAt) {

    public record PlaceInfo(Long placeId, String name) {
    }

    public static CourseItemResponse from(CourseItem item) {
        Place place = item.getPlace();
        return new CourseItemResponse(
                item.getId(),
                new PlaceInfo(place.getId(), place.getName()),
                item.getAddedAt());
    }
}
