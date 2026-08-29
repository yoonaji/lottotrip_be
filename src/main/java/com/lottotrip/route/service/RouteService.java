package com.lottotrip.route.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.place.entity.Place;
import com.lottotrip.route.dto.CarRouteResponse;
import com.lottotrip.route.dto.RouteResponse;
import com.lottotrip.route.dto.WalkRouteResponse;
import com.lottotrip.route.navermap.NaverDirectionsClient;
import com.lottotrip.route.navermap.NaverDirectionsResponse;
import com.lottotrip.route.odsay.OdsayClient;
import com.lottotrip.route.odsay.OdsayResponse;
import com.lottotrip.route.tmap.TmapPedestrianClient;
import com.lottotrip.route.tmap.TmapPedestrianResponse;
import com.lottotrip.slot.entity.SavedSlot;
import com.lottotrip.slot.repository.SavedSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저의 지금 위치에서 당첨 장소까지의 경로 조회. 대중교통(ODsay)·자동차(NCP Directions 5)·
 * 도보(T맵) 셋 다 "그 슬롯이 내 것인지 확인하고 도착지 좌표를 구한다"는 앞부분이 같아서
 * {@link #requireOwnedPlace}로 공유한다.
 *
 * ⚠️ 출발 좌표는 슬롯을 돌렸던 시점의 숙소 좌표(세션에 고정된 값)가 아니라 **매 호출마다
 * 클라이언트가 실어 보내는 실시간 GPS**다 — 슬롯을 돌린 뒤 유저가 이동했을 수 있어서다.
 * 그래서 세션(TripSession)은 소유권 확인에만 쓰이고 좌표는 안 본다.
 *
 * "슬롯 결과 조회"(SlotResultService)와 같은 형태로 만들었다 — 슬롯을 찾고, 소유권을 보고,
 * 바깥 API를 불러 응답을 엮는다. 다만 실패해도 우리 정보가 나가는 슬롯 결과 조회와 달리
 * 여기는 경로 자체가 응답의 전부라서, 바깥 API 호출이 실패하면 그대로 실패시킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final SavedSlotRepository savedSlotRepository;
    private final OdsayClient odsayClient;
    private final NaverDirectionsClient naverDirectionsClient;
    private final TmapPedestrianClient tmapPedestrianClient;

    @Transactional(readOnly = true)
    public RouteResponse getTransitRoute(Long userId, Long slotId, double latitude, double longitude) {
        Place place = requireOwnedPlace(userId, slotId);

        OdsayResponse.Path path = odsayClient.findRecommendedRoute(
                longitude, latitude, place.getLongitude(), place.getLatitude());

        return RouteResponse.from(path);
    }

    @Transactional(readOnly = true)
    public CarRouteResponse getCarRoute(Long userId, Long slotId, double latitude, double longitude) {
        Place place = requireOwnedPlace(userId, slotId);

        NaverDirectionsResponse.TrafastRoute route = naverDirectionsClient.findFastestRoute(
                longitude, latitude, place.getLongitude(), place.getLatitude());

        return CarRouteResponse.from(route);
    }

    @Transactional(readOnly = true)
    public WalkRouteResponse getWalkRoute(Long userId, Long slotId, double latitude, double longitude) {
        Place place = requireOwnedPlace(userId, slotId);

        TmapPedestrianResponse.Properties properties = tmapPedestrianClient.findRoute(
                longitude, latitude, place.getLongitude(), place.getLatitude());

        return WalkRouteResponse.from(properties);
    }

    /**
     * 슬롯을 찾고, 소유권을 보고, 도착지(당첨 장소)를 뽑는다. 출발지는 호출부가 넘겨준
     * 실시간 좌표를 그대로 쓰므로 여기서는 다루지 않는다.
     *
     * @throws CustomException 슬롯이 없거나 남의 슬롯이면 {@link ErrorCode#RESULT_NOT_FOUND}
     */
    private Place requireOwnedPlace(Long userId, Long slotId) {
        SavedSlot slot = savedSlotRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESULT_NOT_FOUND));

        requireOwner(slot, userId);

        return slot.getPlace();
    }

    /**
     * 남의 슬롯은 403이 아니라 404로 답한다. {@code SlotResultService.requireOwner}와 같은 이유 —
     * 403은 "그 번호의 슬롯은 존재한다"를 알려 주는 셈이라 번호를 훑어 남의 기록을 세어 볼 수 있다.
     */
    private void requireOwner(SavedSlot slot, Long userId) {
        Long ownerId = slot.getSession().getUser().getId();
        if (!ownerId.equals(userId)) {
            log.debug("남의 슬롯 경로 조회 시도 — slotId={}, 요청자={}, 소유자={}",
                    slot.getId(), userId, ownerId);
            throw new CustomException(ErrorCode.RESULT_NOT_FOUND);
        }
    }
}
