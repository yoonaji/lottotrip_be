package com.lottotrip.route.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.route.dto.RouteResponse;
import com.lottotrip.route.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로 API. 슬롯 결과 화면의 "여기까지 가는 길" 탭이 부른다.
 */
@Tag(name = "경로", description = "대중교통 길찾기")
@RestController
@RequestMapping("/api/v1/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @Operation(summary = "슬롯 결과 장소까지 대중교통 경로 조회",
            description = "슬롯을 돌릴 때의 출발 좌표(숙소)에서 당첨 장소까지 버스·지하철 경로를 ODsay로 조회한다. "
                    + "출발·도착이 700m 이내이거나 경로가 없으면 404, 남의 슬롯도 404.")
    @GetMapping("/slot/{slotId}")
    public ApiResponse<RouteResponse> getTransitRoute(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long slotId) {
        return ApiResponse.success(routeService.getTransitRoute(userId, slotId));
    }
}
