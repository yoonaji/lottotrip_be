package com.lottotrip.auth.controller;

import com.lottotrip.auth.dto.LoginRequest;
import com.lottotrip.auth.dto.LoginResponse;
import com.lottotrip.auth.dto.RefreshRequest;
import com.lottotrip.auth.dto.RefreshResponse;
import com.lottotrip.auth.service.AuthService;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 API의 HTTP 계약 검증. (roadmap 4-5, tour_api_erd.md 4-1)
 *
 * <p>서비스는 가짜로 끼운다. 여기서 볼 것은 로그인 로직이 아니라
 * <b>요청 형식·응답 형식·에러 상태 코드</b>가 명세와 맞는지다. 로직은 {@code AuthServiceLoginTest}가 본다.
 */
class AuthControllerTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String provider, String providerToken) {
        return """
                {"provider": %s, "providerToken": %s}
                """.formatted(quote(provider), quote(providerToken));
    }

    private String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("로그인에 성공하면 200과 토큰·회원 정보를 내려준다")
    void login_returnsTokens() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willReturn(new LoginResponse("access-jwt", "refresh-jwt",
                        new LoginResponse.UserInfo(1L, "감자러버", true)));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("kakao", "abcd1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-jwt"))
                .andExpect(jsonPath("$.data.user.userId").value(1))
                .andExpect(jsonPath("$.data.user.nickname").value("감자러버"))
                // 명세의 키 이름은 newUser가 아니라 isNewUser다. record의 기본 변환에 맡기면
                // is가 떨어져 나갈 수 있어 실제 응답으로 확인한다. (tour_api_erd.md 4-1)
                .andExpect(jsonPath("$.data.user.isNewUser").value(true));
    }

    // ---------- 요청이 잘못된 경우 ----------

    @Test
    @DisplayName("provider가 없으면 400")
    void login_rejectsMissingProvider() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "abcd1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("providerToken이 비어 있으면 400")
    void login_rejectsBlankProviderToken() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("kakao", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("본문이 JSON 형식이 아니면 400")
    void login_rejectsBrokenBody() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    // ---------- 서비스가 던진 예외 ----------

    @Test
    @DisplayName("소셜 토큰이 유효하지 않으면 401 AUTH_001")
    void login_mapsInvalidProviderToken() throws Exception {
        willThrow(new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN))
                .given(authService).login(any(LoginRequest.class));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("kakao", "expired")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("소셜 서버 장애면 503 COMMON_503")
    void login_mapsServiceUnavailable() throws Exception {
        // 카카오·구글이 응답하지 않는 상황. 토큰 문제가 아니므로 401이 아니다. (4-4-1 결정)
        willThrow(new CustomException(ErrorCode.SERVICE_UNAVAILABLE))
                .given(authService).login(any(LoginRequest.class));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("kakao", "abcd1234")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("COMMON_503"));
    }

    // ---------- 토큰 갱신 ----------

    @Test
    @DisplayName("토큰 갱신에 성공하면 200과 새 액세스 토큰을 내려준다")
    void refresh_returnsNewAccessToken() throws Exception {
        given(authService.refresh(any(RefreshRequest.class)))
                .willReturn(new RefreshResponse("new-access-jwt"));

        mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"refresh-jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-jwt"))
                // 명세상 갱신 응답에는 액세스 토큰만 있다. 리프레시 토큰은 다시 내려주지 않는다.
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("refreshToken이 없으면 400")
    void refresh_rejectsMissingToken() throws Exception {
        mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("리프레시 토큰이 유효하지 않으면 401 AUTH_002")
    void refresh_mapsInvalidRefreshToken() throws Exception {
        willThrow(new CustomException(ErrorCode.INVALID_REFRESH_TOKEN))
                .given(authService).refresh(any(RefreshRequest.class));

        mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"expired\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    // ---------- 그 밖의 매핑 ----------

    @Test
    @DisplayName("지원하지 않는 provider면 400 COMMON_400")
    void login_mapsUnsupportedProvider() throws Exception {
        willThrow(new CustomException(ErrorCode.BAD_REQUEST))
                .given(authService).login(any(LoginRequest.class));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("naver", "abcd1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }
}
