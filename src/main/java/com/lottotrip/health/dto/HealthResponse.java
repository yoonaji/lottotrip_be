package com.lottotrip.health.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 헬스 체크 응답 본문. (tour_api_erd.md 4-2)
 *
 * <p>Entity를 그대로 내려보내지 않고 응답 전용 객체(DTO)를 따로 두는 이유는,
 * 내부 구조가 바뀌어도 API 계약이 흔들리지 않게 하기 위함이다.
 */
@Getter
@AllArgsConstructor
public class HealthResponse {

    private final String status;
    private final String db;
    private final Instant timestamp;
}
