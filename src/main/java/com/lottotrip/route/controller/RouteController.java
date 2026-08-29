package com.lottotrip.route.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.route.dto.CarRouteResponse;
import com.lottotrip.route.dto.RouteResponse;
import com.lottotrip.route.dto.WalkRouteResponse;
import com.lottotrip.route.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로 API. 슬롯 결과 화면의 "여기까지 가는 길" 탭이 부른다.
 *
 * 출발지는 슬롯을 돌렸을 때의 숙소 좌표가 아니라 이 API를 부르는 시점의 실시간 GPS다.
 * 그래서 세 API 모두 {@code latitude}/{@code longitude}를 필수 쿼리 파라미터로 받는다.
 */
@Tag(name = "경로", description = "슬롯 결과 장소까지 대중교통·자동차·도보 길찾기")
@RestController
@RequestMapping("/api/v1/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @Operation(summary = "슬롯 결과 장소까지 대중교통 경로 조회",
            description = "지금 위치(latitude/longitude)에서 당첨 장소까지 버스·지하철 경로를 ODsay로 조회한다. "
                    + "출발·도착이 700m 이내이거나 경로가 없으면 404, 남의 슬롯도 404.")
    @GetMapping("/slot/{slotId}")
    public ApiResponse<RouteResponse> getTransitRoute(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long slotId,
                                                       @RequestParam Double latitude,
                                                       @RequestParam Double longitude) {
        return ApiResponse.success(routeService.getTransitRoute(userId, slotId, latitude, longitude));
    }

    @Operation(summary = "슬롯 결과 장소까지 자동차 경로 조회",
            description = "지금 위치(latitude/longitude)에서 당첨 장소까지 최단시간 자동차 경로를 "
                    + "네이버 클라우드 플랫폼 Directions 5로 조회한다. 경로가 없으면 404, 남의 슬롯도 404.")
    @GetMapping("/slot/{slotId}/car")
    public ApiResponse<CarRouteResponse> getCarRoute(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long slotId,
                                                      @RequestParam Double latitude,
                                                      @RequestParam Double longitude) {
        return ApiResponse.success(routeService.getCarRoute(userId, slotId, latitude, longitude));
    }

    @Operation(summary = "슬롯 결과 장소까지 도보 경로 조회",
            description = "지금 위치(latitude/longitude)에서 당첨 장소까지 도보 경로를 T맵으로 조회한다. "
                    + "경로가 없으면 404, 남의 슬롯도 404.")
    @GetMapping("/slot/{slotId}/walk")
    public ApiResponse<WalkRouteResponse> getWalkRoute(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long slotId,
                                                        @RequestParam Double latitude,
                                                        @RequestParam Double longitude) {
        return ApiResponse.success(routeService.getWalkRoute(userId, slotId, latitude, longitude));
    }
}
