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
     * @param mission draw 때 이 슬롯에서 제시한 미션. 없으면 null — 미션은 곁들이는 정보라
     *                없다고 담은 장소가 목록에서 빠지면 안 된다
     */
    public record Item(Long itemId, PlaceInfo place, MissionInfo mission) {

        public static Item of(CourseItem item, Mission mission, boolean missionCompleted) {
            Place place = item.getPlace();
            return new Item(
                    item.getId(),
                    new PlaceInfo(place.getId(), place.getName()),
                    MissionInfo.of(mission, missionCompleted));
        }
    }

    public record PlaceInfo(Long placeId, String name) {
    }

    /**
     * 코스 항목에 딸린 미션. (결정 23)
     *
     * 앞의 셋은 슬롯 조회(4-3)가 주는 것과 같은 값이다. 원래 `missionId`와 `completed`뿐이라
     * 미션 이름조차 없어 코스 화면이 미션을 그릴 수 없었다.
     *
     * `completed`만 성격이 다르다 — 미션 마스터가 아니라 그 회원의 수행 상태라서
     * 코스 조회에만 있다. 그래서 마스터 값 뒤에 둔다.
     *
     * @param guideDescription 미션 수행 방법 안내. `missions.guide_description`.
     *                         null일 수 있다 — 컬럼이 nullable이라 본문 없이 저장된 미션이 있다
     * @param completed        이 회원이 그 미션을 완료했는가.
     */
    public record MissionInfo(Long missionId, String title, String guideDescription, boolean completed) {

        static MissionInfo of(Mission mission, boolean completed) {
            return mission == null ? null : new MissionInfo(
                    mission.getId(), mission.getTitle(), mission.getGuideDescription(), completed);
        }
    }
}
