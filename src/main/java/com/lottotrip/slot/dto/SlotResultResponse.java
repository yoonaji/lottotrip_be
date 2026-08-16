package com.lottotrip.slot.dto;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.dto.PlaceDetail;

/**
 * 슬롯 결과 조회 응답. (tour_api_erd.md 4-2, roadmap 6-7)
 *
 * ⚠️ **응답 스키마는 아직 확정이 아니다**(미확정 항목 5-10). 실시간 조회로 받아온 값
 * (설명·이미지·요금·무장애 정보) 중 무엇을 실을지 **프론트와 상의해 정하기로 했다.**
 * 지금은 {@link PlaceDetail}이 담는 소개글·홈페이지까지 내보낸다.
 * (`tour_api_erd.md` 4-3의 응답 예시는 현재 구현에 맞춰 갱신했다.)
 *
 * @param place   장소 정보. DB에 담아 둔 값 + TourAPI 실시간 조회 결과가 섞여 있다
 * @param mission 이 장소의 미션. **없을 수 있다**
 */
public record SlotResultResponse(
        Long slotId,
        PlaceDetail place,
        MissionInfo mission
) {

    public record MissionInfo(Long missionId, String title) {
    }

    public static SlotResultResponse of(Long slotId, PlaceDetail place, Mission mission) {
        return new SlotResultResponse(
                slotId,
                place,
                mission == null ? null : new MissionInfo(mission.getId(), mission.getTitle()));
    }
}
