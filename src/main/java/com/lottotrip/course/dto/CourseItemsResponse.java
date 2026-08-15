package com.lottotrip.course.dto;

import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;

import java.util.List;

/**
 * 코스 조회 응답. (tour_api_erd.md 4-4)
 *
 * <p>목록을 배열로 바로 내보내지 않고 {@code items}로 한 겹 감싼다. 최상위가 배열이면
 * 나중에 코스 제목·총 개수 같은 값을 더할 때 <b>응답 모양을 통째로 바꿔야</b> 한다.
 */
public record CourseItemsResponse(List<Item> items) {

    /**
     * @param mission 이 장소의 미션. 없으면 null — 미션은 곁들이는 정보라
     *                없다고 담은 장소가 목록에서 빠지면 안 된다
     */
    public record Item(Long itemId, PlaceInfo place, MissionInfo mission) {

        public static Item of(CourseItem item, Mission mission) {
            Place place = item.getPlace();
            return new Item(
                    item.getId(),
                    new PlaceInfo(place.getId(), place.getName()),
                    MissionInfo.of(mission));
        }
    }

    public record PlaceInfo(Long placeId, String name) {
    }

    /**
     * @param completed 미션 완료 여부.
     *                  ⚠️ <b>지금은 항상 false다</b> — 완료를 기록하는 {@code user_missions}를
     *                  채우는 것이 8-2라서, 아직 완료를 남길 방법 자체가 없다.
     *                  <b>판정 방식은 8단계에서 정한다</b>(roadmap 7-3)
     */
    public record MissionInfo(Long missionId, boolean completed) {

        /** 완료 여부를 아직 판정하지 못하므로 false로 채운다. 8-2에서 실제 값이 들어온다. */
        static MissionInfo of(Mission mission) {
            return mission == null ? null : new MissionInfo(mission.getId(), false);
        }
    }
}
