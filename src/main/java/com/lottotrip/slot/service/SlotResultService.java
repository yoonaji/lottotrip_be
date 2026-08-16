package com.lottotrip.slot.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.place.dto.PlaceDetail;
import com.lottotrip.place.service.PlaceDetailService;
import com.lottotrip.slot.dto.SlotResultResponse;
import com.lottotrip.slot.entity.SavedSlot;
import com.lottotrip.slot.repository.SavedSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슬롯 결과 조회. **"룰렛 세부사항 조회"를 겸한다.** (roadmap 6-7)
 *
 * **공모전 규정(결정 7 — 오픈API 실시간 호출 필수)을 지탱하는 두 지점 중 하나다.**
 * 한때는 여기가 유일했으나(결정 10에서는 추첨이 DB 조회였다),
 * 결정 12로 `draw`도 실시간 호출을 하게 되어 **지금은 두 곳이다.**
 * 실시간 호출 자체는 5-10의 {@link PlaceDetailService}가 맡고, 이 클래스는
 * **슬롯을 찾고 권한을 보고 응답을 엮는** 일을 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotResultService {

    private final SavedSlotRepository savedSlotRepository;
    private final MissionRepository missionRepository;
    private final PlaceDetailService placeDetailService;

    /**
     * 뽑았던 슬롯 하나를 조회한다.
     *
     * **실패해도 우리 정보는 나간다.** TourAPI 호출은 {@link PlaceDetailService}가 안에서
     * 실패를 삼키므로, 공공데이터포털이 멈춰도 사용자는 방금 뽑은 장소를 볼 수 있다.
     * `liveDetailLoaded`가 false로 내려가 "지금 못 받아왔다"를 프론트가 구분할 수 있다.
     *
     * @throws CustomException 슬롯이 없거나 **남의 슬롯이면** {@link ErrorCode#RESULT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public SlotResultResponse getResult(Long userId, Long slotId) {
        SavedSlot slot = savedSlotRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESULT_NOT_FOUND));

        // 소유권을 바깥 호출보다 먼저 본다. 나중에 보면 남의 슬롯을 조회하는 것만으로도
        // 우리 TourAPI 일일 할당량이 깎인다.
        requireOwner(slot, userId);

        PlaceDetail place = placeDetailService.describe(slot.getPlace());
        return SlotResultResponse.of(slot.getId(), place, missionOf(slot));
    }

    /**
     * 이 슬롯이 이 회원의 것인지 확인한다.
     *
     * **403이 아니라 404로 답한다.** 403은 "그 번호의 슬롯은 존재한다"를 알려 주는 셈이라,
     * 번호를 훑어 남이 무엇을 얼마나 뽑았는지 세어 볼 수 있다.
     */
    private void requireOwner(SavedSlot slot, Long userId) {
        Long ownerId = slot.getSession().getUser().getId();
        if (!ownerId.equals(userId)) {
            log.debug("남의 슬롯 조회 시도 — slotId={}, 요청자={}, 소유자={}",
                    slot.getId(), userId, ownerId);
            throw new CustomException(ErrorCode.RESULT_NOT_FOUND);
        }
    }

    /**
     * 이 슬롯에서 제시했던 미션 조회. (roadmap 6-13, 결정 14)
     * `saved_slots.mission_id`에서 조회.
     */
    private Mission missionOf(SavedSlot slot) {
        return slot.getMission();
    }
}
