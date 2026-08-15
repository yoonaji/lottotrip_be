package com.lottotrip.course.dto;

/**
 * 코스 항목 삭제 응답. (tour_api_erd.md 4-4)
 *
 * <p><b>{@code deleted}가 항상 true인데도 담는 이유:</b> 명세가 그렇게 정해져 있다.
 * 지우지 못한 경우는 이 응답이 아니라 {@code 404 ITEM_NOT_FOUND}로 나가므로,
 * 이 값이 false가 되는 경우는 없다.
 */
public record CourseItemRemoveResponse(Long itemId, boolean deleted) {

    public static CourseItemRemoveResponse of(Long itemId) {
        return new CourseItemRemoveResponse(itemId, true);
    }
}
