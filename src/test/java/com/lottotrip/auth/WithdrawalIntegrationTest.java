package com.lottotrip.auth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.support.StubbedSocialServerConfig;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 탈퇴 통합 검증. (roadmap 9-5, 결정 20)
 *
 * 여기서만 확인되는 것: "탈퇴한 사람이 정말 못 들어오는가". 소프트 삭제는 회원 행이 남아서
 * 조회 조건을 한 곳이라도 빠뜨리면 탈퇴자가 그대로 API를 쓴다 — 전 구간을 지나야 드러난다.
 *
 * 카카오만 가짜다. 나머지(시큐리티 필터·컨트롤러·서비스·JPA·PostgreSQL)는 전부 진짜다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubbedSocialServerConfig.class)
@TestPropertySource(properties = "oauth.kakao.app-id=" + StubbedSocialServerConfig.KAKAO_APP_ID)
@Transactional
class WithdrawalIntegrationTest extends PostgresContainerSupport {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String WITHDRAW_PATH = "/api/v1/auth/me";
    private static final String COURSE_ITEMS_PATH = "/api/v1/course/items";
    private static final String SLOT_DRAW_PATH = "/api/v1/slot/draw";

    private static final String KAKAO_RESPONSE = """
            {
              "id": 987654321,
              "kakao_account": {
                "email": "potato@example.com",
                "profile": { "nickname": "감자러버", "profile_image_url": "https://img.kakao.com/p.png" }
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockRestServiceServer kakaoServer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAuthRepository socialAuthRepository;

    @BeforeEach
    void setUp() {
        kakaoServer.reset();
        StubbedSocialServerConfig.expectValidTokenInfo(kakaoServer);
    }

    // ---------- 도우미 ----------

    private void expectKakaoCalls(int count) {
        kakaoServer.expect(ExpectedCount.times(count), requestTo(StubbedSocialServerConfig.KAKAO_USER_INFO_URI))
                .andRespond(withSuccess(KAKAO_RESPONSE, MediaType.APPLICATION_JSON));
    }

    private MvcResult login() throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "kakao", "providerToken": "valid-kakao-token"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result;
    }

    /**
     * 응답 JSON에서 값을 하나 꺼내 문자열로 돌려준다.
     *
     * ⚠️ 결과를 `Object`로 한 번 받는 것이 중요하다. `String.valueOf(JsonPath.read(...))`처럼 바로 넘기면
     * `JsonPath.read`의 반환 타입이 제네릭이라 자바가 `String.valueOf(char[])` 쪽으로 해석해
     * 실행 시점에 `ClassCastException`이 난다.
     */
    private String read(MvcResult result, String path) throws Exception {
        Object value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }

    private String accessTokenOf(MvcResult login) throws Exception {
        return read(login, "$.data.accessToken");
    }

    private Long userIdOf(MvcResult login) throws Exception {
        return Long.valueOf(read(login, "$.data.user.userId"));
    }

    private void withdraw(String accessToken) throws Exception {
        mockMvc.perform(delete(WITHDRAW_PATH).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.deletedAt").isNotEmpty());
    }

    // ---------- 탈퇴 처리 ----------

    @Test
    @DisplayName("탈퇴하면 식별정보가 실제로 지워진다 — 회원 행은 남는다")
    void withdrawErasesIdentifyingData() throws Exception {
        expectKakaoCalls(1);
        MvcResult login = login();
        Long userId = userIdOf(login);

        withdraw(accessTokenOf(login));

        // 행은 남아 있어야 한다. FK 네 개가 가리키고 있기 때문이다.
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getNickname()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("탈퇴하면 소셜 연결이 끊긴다")
    void withdrawDeletesSocialAuth() throws Exception {
        expectKakaoCalls(1);
        MvcResult login = login();

        withdraw(accessTokenOf(login));

        assertThat(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.KAKAO, "987654321"))
                .isEmpty();
    }

    // ---------- 🔴 탈퇴 후 차단 (이 테스트가 이 단계의 핵심이다) ----------

    @Test
    @DisplayName("탈퇴한 회원의 토큰으로는 코스 조회가 막힌다")
    void withdrawnUserCannotUseCourseApi() throws Exception {
        expectKakaoCalls(1);
        MvcResult login = login();
        String token = accessTokenOf(login);

        withdraw(token);

        // 토큰 자체는 아직 유효하다(우리 JWT는 저장하지 않아 즉시 무효화가 안 된다).
        // 그래서 회원을 조회하는 지점이 탈퇴자를 걸러야 한다.
        mockMvc.perform(get(COURSE_ITEMS_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("탈퇴한 회원의 토큰으로는 슬롯도 돌릴 수 없다")
    void withdrawnUserCannotDrawSlot() throws Exception {
        expectKakaoCalls(1);
        MvcResult login = login();
        String token = accessTokenOf(login);

        withdraw(token);

        // 세션을 만들기 전에 회원을 조회하므로 TourAPI까지 가지 않고 막힌다.
        mockMvc.perform(post(SLOT_DRAW_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude": 37.7519, "longitude": 128.8761,
                                 "budget": 50000, "transport": "walk"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴한 회원은 토큰을 갱신할 수 없다 — 리프레시로 되살아나면 안 된다")
    void withdrawnUserCannotRefresh() throws Exception {
        expectKakaoCalls(1);
        MvcResult login = login();
        String refreshToken = read(login, "$.data.refreshToken");

        withdraw(accessTokenOf(login));

        mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("두 번 탈퇴하면 401 — 이미 지운 것을 또 지우지 않는다")
    void secondWithdrawIsRejected() throws Exception {
        expectKakaoCalls(1);
        MvcResult login = login();
        String token = accessTokenOf(login);

        withdraw(token);

        mockMvc.perform(delete(WITHDRAW_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰 없이 탈퇴 요청하면 401 — 남의 계정을 지울 수 없다")
    void withdrawRequiresAuthentication() throws Exception {
        mockMvc.perform(delete(WITHDRAW_PATH))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 재가입 ----------

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 신규 가입이다 — 탈퇴 계정이 되살아나지 않는다")
    void reLoginCreatesNewAccount() throws Exception {
        expectKakaoCalls(2); // 최초 로그인 + 탈퇴 후 재로그인
        MvcResult first = login();
        Long firstUserId = userIdOf(first);

        withdraw(accessTokenOf(first));

        MvcResult second = login();

        // 같은 카카오 계정인데도 새 회원이어야 한다. social_auth를 지웠기 때문이다.
        assertThat(read(second, "$.data.user.isNewUser")).isEqualTo("true");
        assertThat(userIdOf(second)).isNotEqualTo(firstUserId);

        // 옛 회원은 익명 껍데기로 남아 있고, 새 회원과 연결되지 않는다.
        assertThat(userRepository.findById(firstUserId).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("재가입한 회원은 정상적으로 API를 쓸 수 있다")
    void reJoinedUserCanUseApi() throws Exception {
        expectKakaoCalls(2);
        MvcResult first = login();
        withdraw(accessTokenOf(first));

        MvcResult second = login();

        mockMvc.perform(get(COURSE_ITEMS_PATH).header("Authorization", "Bearer " + accessTokenOf(second)))
                .andExpect(status().isOk());
    }
}
