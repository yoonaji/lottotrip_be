package com.lottotrip.course.dto;

/**
 * 코스 항목 삭제 응답. (tour_api_erd.md 4-4)
 */
public record CourseItemRemoveResponse(Long itemId, boolean deleted) {

    public static CourseItemRemoveResponse of(Long itemId) {
        return new CourseItemRemoveResponse(itemId, true);
    }
}
