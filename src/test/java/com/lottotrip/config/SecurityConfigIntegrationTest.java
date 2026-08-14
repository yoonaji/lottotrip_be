package com.lottotrip.config;

import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시큐리티 설정 검증. (roadmap 4-2)
 *
 * <p>"어떤 경로가 토큰 없이 열려 있고, 어떤 경로가 막혀 있는가"는 애플리케이션을 실제로 띄워야
 * 확인할 수 있다. 필터 단위 테스트는 "토큰을 읽는가"만 볼 수 있을 뿐,
 * "토큰이 없을 때 실제로 막히는가"는 알 수 없기 때문이다.
 *
 * <p>아직 구현되지 않은 인증 API(4-5~4-7)에 대한 검증은 <b>상태 코드가 401이 아님</b>으로 확인한다.
 * 경로가 열려 있으면 시큐리티를 통과해 "핸들러 없음(404)"까지 도달하기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest extends PostgresContainerSupport {

    /**
     * 인증이 필요한 아무 경로. 시큐리티는 핸들러 유무와 무관하게 먼저 막는다.
     *
     * <p><b>일부러 존재하지 않는 경로를 쓴다.</b> 실제 API(예전에는 {@code /api/v1/slot/draw})를
     * 가리키면, 그 API가 구현되는 순간 응답이 404에서 400(본문 검증 실패)으로 바뀌어
     * <b>시큐리티와 무관한 이유로 이 테스트가 깨진다.</b> 실제로 6-6에서 그렇게 됐다.
     * 여기서 확인하려는 것은 "인증을 통과하는가"뿐이므로 핸들러가 없는 편이 낫다.
     */
    private static final String PROTECTED_PATH = "/api/v1/__security-probe";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    // ---------- 열린 경로 ----------

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/health", "/actuator/health"})
    @DisplayName("헬스 체크는 토큰 없이 열려 있다")
    void healthIsPublic(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh"})
    @DisplayName("로그인·토큰 갱신은 토큰 없이 열려 있다")
    void authEntryPointsArePublic(String path) throws Exception {
        // 토큰을 받기 위한 API가 토큰을 요구하면 아무도 로그인할 수 없다.
        //
        // 확인할 것은 "시큐리티를 통과하는가"뿐이므로 401만 아니면 된다. 실제 상태 코드는
        // 구현 여부에 따라 달라진다. 로그인(4-5)은 구현돼 있어 본문이 없으니 400,
        // 토큰 갱신(4-6)은 아직 핸들러가 없어 404다. 둘 다 시큐리티는 지나온 것이다.
        mockMvc.perform(post(path))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                                .as("인증이 필요 없는 경로여야 한다")
                                .isNotEqualTo(401));
    }

    // ---------- 막힌 경로 ----------

    @Test
    @DisplayName("토큰 없이 보호된 API를 호출하면 401로 막는다")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("401 응답도 공통 응답 포맷과 COMMON_401을 따른다")
    void unauthorizedResponseFollowsCommonFormat() throws Exception {
        // 시큐리티가 막은 요청은 컨트롤러까지 가지 않아 GlobalExceptionHandler가 관여하지 못한다.
        // 그대로 두면 스프링 기본 형식으로 응답이 나가 프론트가 두 가지 포맷을 다뤄야 한다.
        mockMvc.perform(post(PROTECTED_PATH))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer broken.token.value", "Bearer ", "Basic dXNlcjpwYXNz"})
    @DisplayName("토큰이 유효하지 않으면 401로 막는다")
    void rejectsRequestWithInvalidToken(String authorization) throws Exception {
        mockMvc.perform(post(PROTECTED_PATH).header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("리프레시 토큰으로는 보호된 API를 호출할 수 없다")
    void rejectsRefreshTokenOnProtectedApi() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + jwtProvider.createRefreshToken(1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 액세스 토큰이 있으면 시큐리티를 통과한다")
    void allowsRequestWithValidAccessToken() throws Exception {
        // 401이 아니라 404라는 것은 "인증은 통과했는데 그런 API가 아직 없다"는 뜻이다.
        mockMvc.perform(post(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isNotFound());
    }

    // ---------- 무상태 ----------

    @Test
    @DisplayName("인증에 성공해도 세션(JSESSIONID)을 만들지 않는다")
    void doesNotCreateHttpSession() throws Exception {
        // JWT는 토큰 자체에 사용자 정보가 들어 있어 서버가 로그인 상태를 기억할 필요가 없다.
        // 세션을 만들면 서버를 여러 대로 늘릴 때 "어느 서버가 그 세션을 갖고 있는가" 문제가 생긴다.
        mockMvc.perform(post(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(result -> {
                    if (result.getRequest().getSession(false) != null) {
                        throw new AssertionError("HTTP 세션이 생성되었다. 무상태(stateless) 설정을 확인하라.");
                    }
                });
    }
}
