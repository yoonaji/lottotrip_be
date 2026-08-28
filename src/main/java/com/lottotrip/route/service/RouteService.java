package com.lottotrip.route.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
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
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.slot.repository.SavedSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슬롯을 돌렸던 출발 좌표(숙소)에서 당첨 장소까지의 경로 조회. 대중교통(ODsay)·자동차(NCP Directions 5)
 * 둘 다 "그 슬롯의 출발·도착 좌표를 구한다"는 앞부분이 같아서 {@link #loadRouteOrigin}으로 공유한다.
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
    public RouteResponse getTransitRoute(Long userId, Long slotId) {
        RouteOrigin origin = loadRouteOrigin(userId, slotId);

        OdsayResponse.Path path = odsayClient.findRecommendedRoute(
                origin.startLongitude(), origin.startLatitude(),
                origin.endLongitude(), origin.endLatitude());

        return RouteResponse.from(path);
    }

    @Transactional(readOnly = true)
    public CarRouteResponse getCarRoute(Long userId, Long slotId) {
        RouteOrigin origin = loadRouteOrigin(userId, slotId);

        NaverDirectionsResponse.TrafastRoute route = naverDirectionsClient.findFastestRoute(
                origin.startLongitude(), origin.startLatitude(),
                origin.endLongitude(), origin.endLatitude());

        return CarRouteResponse.from(route);
    }

    @Transactional(readOnly = true)
    public WalkRouteResponse getWalkRoute(Long userId, Long slotId) {
        RouteOrigin origin = loadRouteOrigin(userId, slotId);

        TmapPedestrianResponse.Properties properties = tmapPedestrianClient.findRoute(
                origin.startLongitude(), origin.startLatitude(),
                origin.endLongitude(), origin.endLatitude());

        return WalkRouteResponse.from(properties);
    }

    /**
     * 슬롯을 찾고, 소유권을 보고, 출발·도착 좌표를 뽑는다.
     *
     * @throws CustomException 슬롯이 없거나 남의 슬롯이면 {@link ErrorCode#RESULT_NOT_FOUND},
     *                          출발 좌표를 모르면(탈퇴로 지워짐) {@link ErrorCode#ROUTE_NOT_FOUND}
     */
    private RouteOrigin loadRouteOrigin(Long userId, Long slotId) {
        SavedSlot slot = savedSlotRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESULT_NOT_FOUND));

        requireOwner(slot, userId);

        TripSession session = slot.getSession();
        Double startLatitude = session.getAccommodationLatitude();
        Double startLongitude = session.getAccommodationLongitude();
        // 탈퇴 시 숙소 좌표가 지워진다(TripSession.eraseAccommodationLocation, 결정 20).
        // 그런 세션은 출발지를 모르므로 경로를 만들 수 없다.
        if (startLatitude == null || startLongitude == null) {
            throw new CustomException(ErrorCode.ROUTE_NOT_FOUND);
        }

        return new RouteOrigin(startLongitude, startLatitude,
                slot.getPlace().getLongitude(), slot.getPlace().getLatitude());
    }

    private record RouteOrigin(double startLongitude, double startLatitude,
                                double endLongitude, double endLatitude) {
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
