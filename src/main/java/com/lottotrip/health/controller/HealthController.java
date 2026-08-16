package com.lottotrip.health.controller;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.health.dto.HealthResponse;
import com.lottotrip.health.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 시스템 상태 확인 API. (tour_api_erd.md 4-2)
 *
 * 인증이 필요 없는 엔드포인트다. 로드밸런서·모니터링이 주기적으로 호출한다.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private static final String STATUS_UP = "UP";

    private final HealthService healthService;

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        // DB가 죽었으면 예외를 던진다. 상태 코드·응답 본문 변환은 GlobalExceptionHandler가 맡으므로
        // 컨트롤러가 503을 직접 조립할 필요가 없다.
        if (!healthService.isDatabaseUp()) {
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        // 초 단위로 잘라낸다. 나노초까지 내려보내면 명세 예시("2026-07-12T12:00:00Z")와 형식이 달라진다.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return ApiResponse.success(new HealthResponse(STATUS_UP, STATUS_UP, now));
    }
}
