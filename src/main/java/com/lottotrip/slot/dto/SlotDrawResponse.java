package com.lottotrip.slot.dto;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;

/**
 * 슬롯 돌리기 응답. (tour_api_erd.md 4-2)
 *
 * **Entity를 그대로 내보내지 않는 이유.** `Place`에는 `contentId`·
 * `modifiedTime`처럼 우리 사정에 쓰는 값이 함께 있다. 그대로 내보내면 필요 없는 것이 새어 나가고,
 * 반대로 **테이블 구조를 바꿀 때마다 API 응답이 따라 흔들린다.** 응답 모양을 따로 두면
 * DB는 DB대로, API는 API대로 바꿀 수 있다.
 *
 * @param slotId  `saved_slots.slot_id`. 7단계 코스 추가가 이 값을 참조한다
 * @param place   뽑힌 장소
 * @param mission 함께 제시할 미션. **없을 수 있다** — 생성까지 실패한 경우이며,
 *                그때도 장소는 정상적으로 뽑혔으므로 응답을 실패시키지 않는다
 */
public record SlotDrawResponse(
        Long slotId,
        PlaceInfo place,
        MissionInfo mission
) {

    /**
     * @param category    한글 표시 이름("자연관광지"·"음식점"). DB에는 상수명(`NATURE_ATTRACTION`)으로 저장된다.
     *                    🔄 **자체 분류(`해변`·`카페`)는 6-15에서 폐기됐다** — TourAPI `cat2` 25종을 쓴다.
     *                    해수욕장은 `자연관광지`, 카페는 `음식점`으로 나간다
     * @param distanceKm  기준 좌표로부터의 거리. 소수 첫째 자리까지만 담는다
     * @param thumbnailUrl 대표 이미지. 없으면 null.
     *                    ⚠️ **채움률 18%는 옛 수치다** — 음식점·숙박이 섞인 표본이었고,
     *                    여행지 3종만 보면 92%다(1,125/1,218건, 2026-08-15 실측)
     */
    public record PlaceInfo(
            Long placeId,
            String name,
            String category,
            Double latitude,
            Double longitude,
            Double distanceKm,
            String thumbnailUrl
    ) {
    }

    public record MissionInfo(Long missionId, String title) {
    }

    /**
     * 뽑은 결과를 응답 모양으로 옮긴다. (roadmap 6-13)
     *
     * ⚠️ **거리와 이미지는 우리 DB가 아니라 방금 받은 API 응답에서 온다.**
     * 좌표 기반 조회가 `dist`(거리)와 `firstimage`(대표 이미지)를 함께 주므로
     * 거리를 계산할 필요도, `place_media`를 다시 조회할 필요도 없다.
     * 거리를 소수 첫째 자리로 줄이는 것은 부르는 쪽이 한다 — 반경이 10km·30km 단위라
     * 100m보다 세밀한 값은 쓸 데가 없다.
     *
     * @param distanceKm   기준 좌표로부터의 거리. 이미 반올림된 값을 받는다
     * @param thumbnailUrl 대표 이미지. 없으면 null
     */
    public static SlotDrawResponse of(Long slotId, Place place, Double distanceKm,
                                      String thumbnailUrl, Mission mission) {
        return new SlotDrawResponse(
                slotId,
                new PlaceInfo(
                        place.getId(),
                        place.getName(),
                        place.getCategory().getDisplayName(),
                        place.getLatitude(),
                        place.getLongitude(),
                        distanceKm,
                        thumbnailUrl),
                mission == null ? null : new MissionInfo(mission.getId(), mission.getTitle()));
    }
}
