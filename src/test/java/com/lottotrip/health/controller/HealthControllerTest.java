package com.lottotrip.health.controller;

import com.lottotrip.common.exception.GlobalExceptionHandler;
import com.lottotrip.health.service.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private MockMvc mockMvc;
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        // 실제 DB 대신 가짜(mock) 객체를 끼운다. 컨트롤러가 "DB 상태에 따라 무엇을 하는지"만
        // 검증하는 것이 목적이므로, 진짜 DB를 띄울 필요가 없다.
        healthService = mock(HealthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("DB가 정상이면 200과 공통 응답 포맷으로 응답한다")
    void health_returnsOk() throws Exception {
        given(healthService.isDatabaseUp()).willReturn(true);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("DB가 정상이면 status와 db 모두 UP이다")
    void health_statusAndDbAreUp() throws Exception {
        given(healthService.isDatabaseUp()).willReturn(true);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.db").value("UP"));
    }

    @Test
    @DisplayName("응답 data의 timestamp는 ISO-8601 UTC 형식이다")
    void health_timestampIsIsoUtc() throws Exception {
        given(healthService.isDatabaseUp()).willReturn(true);

        // tour_api_erd.md 4-2 예시: "2026-07-12T12:00:00Z"
        // 프론트(Swift)가 ISO8601DateFormatter로 그대로 파싱할 수 있어야 하므로 형식을 고정한다.
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(jsonPath("$.data.timestamp")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")));
    }

    @Test
    @DisplayName("DB 연결이 끊기면 503과 COMMON_503으로 응답한다")
    void health_returnsServiceUnavailable_whenDbIsDown() throws Exception {
        given(healthService.isDatabaseUp()).willReturn(false);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("COMMON_503"))
                .andExpect(jsonPath("$.error.message").value("서비스를 이용할 수 없습니다."));
    }
}
