package com.lottotrip.course.dto;

import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;

import java.util.List;

/**
 * 코스 조회 응답. (tour_api_erd.md 4-4)
 */
public record CourseItemsResponse(List<Item> items) {

    /**
     * @param slotId  이 항목이 담은 슬롯 번호(`course_items.slot_id`). 웹 코스 화면이 항목에서
     *                슬롯 상세(4-3)·경로 조회(10장)로 건너가려면 슬롯 번호가 있어야 하는데,
     *                코스 응답에는 `itemId`뿐이라 버튼을 달 수 없었다 (2026-09-12 웹 요청).
     *                삭제는 여전히 `itemId`로 한다 — 이 값은 조회용이다.
     * @param mission draw 때 이 슬롯에서 제시한 미션. 없으면 null — 미션은 곁들이는 정보라
     *                없다고 담은 장소가 목록에서 빠지면 안 된다
     */
    public record Item(Long itemId, Long slotId, PlaceInfo place, MissionInfo mission) {

        public static Item of(CourseItem item, Mission mission, boolean missionCompleted) {
            Place place = item.getPlace();
            return new Item(
                    item.getId(),
                    item.getSlot().getId(),
                    new PlaceInfo(place.getId(), place.getName()),
                    MissionInfo.of(mission, missionCompleted));
        }
    }

    public record PlaceInfo(Long placeId, String name) {
    }

    /**
     * @param completed 이 회원이 그 미션을 완료했는가.
     */
    public record MissionInfo(Long missionId, boolean completed) {

        static MissionInfo of(Mission mission, boolean completed) {
            return mission == null ? null : new MissionInfo(mission.getId(), completed);
        }
    }
}
