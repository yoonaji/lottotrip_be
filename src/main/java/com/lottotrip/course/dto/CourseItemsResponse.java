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
     * @param completed 이 회원이 그 미션을 완료했는가.
     *                  <b>판정 근거는 {@code user_missions}에 줄이 있는가뿐이다</b>(roadmap 9-1-1).
     *                  그 줄은 GPS 인증을 통과했을 때만 생기므로(8-1·8-2), 결국 "그 장소에
     *                  실제로 다녀왔는가"와 같은 말이 된다
     */
    public record MissionInfo(Long missionId, boolean completed) {

        static MissionInfo of(Mission mission, boolean completed) {
            return mission == null ? null : new MissionInfo(mission.getId(), completed);
        }
    }
}
