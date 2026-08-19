package com.lottotrip.course.dto;

import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.place.entity.Place;

import java.time.LocalDateTime;

/**
 * 코스에 담은 결과. (tour_api_erd.md 4-4)
 *
 * @param addedAt 담은 시각. `course_items.added_at`이 그대로 나간다
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
