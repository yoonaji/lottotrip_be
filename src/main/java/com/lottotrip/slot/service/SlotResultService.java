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
 * 슬롯 결과 조회. <b>"룰렛 세부사항 조회"를 겸한다.</b> (roadmap 6-7, 결정 10)
 *
 * <p><b>이 서비스가 공모전 규정을 지탱한다.</b> 결정 10으로 추첨이 DB에서만 이뤄지므로,
 * <b>사용자 요청에 반응해 공공데이터 API를 부르는 지점은 여기 하나뿐이다</b>(결정 7 — 오픈API 실시간 호출 필수).
 * 실시간 호출 자체는 5-10의 {@link PlaceDetailService}가 맡고, 이 클래스는
 * <b>슬롯을 찾고 권한을 보고 응답을 엮는</b> 일을 한다.
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
     * <p><b>실패해도 우리 정보는 나간다.</b> TourAPI 호출은 {@link PlaceDetailService}가 안에서
     * 실패를 삼키므로, 공공데이터포털이 멈춰도 사용자는 방금 뽑은 장소를 볼 수 있다.
     * {@code liveDetailLoaded}가 false로 내려가 "지금 못 받아왔다"를 프론트가 구분할 수 있다.
     *
     * @throws CustomException 슬롯이 없거나 <b>남의 슬롯이면</b> {@link ErrorCode#RESULT_NOT_FOUND}
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
     * <p><b>403이 아니라 404로 답한다.</b> 403은 "그 번호의 슬롯은 존재한다"를 알려 주는 셈이라,
     * 번호를 훑어 남이 무엇을 얼마나 뽑았는지 세어 볼 수 있다. 없는 것과 남의 것을 같게 다루면
     * 그런 추측이 불가능해진다.
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
     * 이 슬롯의 장소에 붙은 미션.
     *
     * <p>⚠️ <b>draw 때 보여 준 그 미션이라는 보장이 없다.</b> {@code saved_slots}에는
     * {@code mission_id}가 없어서(ERD 기준) 어떤 미션을 제시했는지 남기지 않는다.
     * 그래서 <b>가장 먼저 등록된 것</b>을 돌려준다 — 적어도 같은 슬롯을 여러 번 조회할 때
     * 미션이 매번 바뀌지는 않는다. 이 어긋남을 어떻게 없앨지는 <b>미확정</b>이며,
     * {@code saved_slots}에 컬럼을 더하는 방법이 유력하다(스키마 변경이라 회의에서 정한다).
     */
    private Mission missionOf(SavedSlot slot) {
        return missionRepository.findFirstByPlaceIdOrderByIdAsc(slot.getPlace().getId())
                .orElse(null);
    }
}
