package com.lottotrip.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 헬스 체크 통합 테스트. (roadmap 2-3)
 *
 * <p>지금까지의 단위 테스트는 컨트롤러만 따로 떼어 내(standaloneSetup) 검증했다.
 * 그래서 "필터·시큐리티·JSON 직렬화까지 다 거친 진짜 요청"이 어떻게 되는지는 확인하지 못했다.
 * 통합 테스트는 애플리케이션 전체를 실제로 띄워서, 요청이 들어와 응답이 나갈 때까지의
 * 전 구간이 명세대로 동작하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HealthIntegrationTest {

    /**
     * 테스트 실행 중에만 살아 있는 진짜 PostgreSQL.
     *
     * <p>헬스 체크는 "DB에 실제로 붙을 수 있는가"를 확인하는 API다. 가짜 DataSource로는
     * 그 확인 자체가 무의미하므로, 운영과 같은 종류의 DB(PostgreSQL 16)를 컨테이너로 띄운다.
     * H2 같은 다른 DB로 대체하면 "테스트는 통과하는데 운영에서 안 되는" 상황이 생길 수 있다.
     */
    @Container
    @ServiceConnection // 컨테이너의 접속 정보(url/user/password)를 스프링 DataSource 설정에 자동으로 꽂아 준다.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("인증 없이 호출해도 200으로 응답한다")
    void health_isAccessibleWithoutAuthentication() throws Exception {
        // tour_api_erd.md 4-2: "인증 불필요".
        // 로드밸런서·모니터링이 토큰 없이 주기적으로 호출하는 엔드포인트다.
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공통 응답 포맷(success/data/error)을 그대로 지킨다")
    void health_followsCommonResponseFormat() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("실제 DB에 연결된 상태이면 status와 db 모두 UP이다")
    void health_reportsUp_whenConnectedToRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.db").value("UP"));
    }

    @Test
    @DisplayName("timestamp는 ISO-8601 UTC 형식으로 직렬화된다")
    void health_serializesTimestampAsIsoUtc() throws Exception {
        // 단위 테스트에서도 형식을 확인했지만, 그때는 컨트롤러만 띄운 상태였다.
        // 실제 애플리케이션의 Jackson 설정이 Instant를 숫자(epoch)로 바꿔 버리는 경우가 있어
        // 전체 컨텍스트에서 한 번 더 확인한다.
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(jsonPath("$.data.timestamp")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")));
    }
}
