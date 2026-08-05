package com.lottotrip.health.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.health.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 시스템 상태 확인 API. (tour_api_erd.md 4-2)
 *
 * <p>인증이 필요 없는 유일한 GET 엔드포인트다. 로드밸런서·모니터링이 주기적으로 호출한다.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        // 초 단위로 잘라낸다. 나노초까지 내려보내면 명세 예시("2026-07-12T12:00:00Z")와 형식이 달라진다.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return ApiResponse.success(new HealthResponse("UP", now));
    }
}
