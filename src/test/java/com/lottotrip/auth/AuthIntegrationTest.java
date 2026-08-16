package com.lottotrip.auth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.support.StubbedSocialServerConfig;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 흐름 통합 검증. (roadmap 4-8)
 *
 * <p>지금까지의 테스트는 조각을 하나씩 봤다. 여기서는 <b>실제 서버를 띄우고 진짜 DB에 저장하며</b>
 * HTTP 요청부터 응답까지 전 구간을 지난다. 조각별 테스트가 모두 통과해도 조립하면 안 되는 경우가 있다.
 * 예를 들어 "로그인으로 받은 토큰이 실제로 다른 API에서 통하는가"는 여기서만 확인된다.
 *
 * <p>카카오만 가짜로 바꾼다. 우리 코드 바깥이라 통제할 수 없기 때문이다. 나머지(시큐리티 필터·
 * 컨트롤러·서비스·JPA·PostgreSQL)는 전부 진짜다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubbedSocialServerConfig.class)
// 테스트마다 트랜잭션을 열고 끝나면 되돌린다. 앞 테스트가 만든 회원이 다음 테스트에 남지 않는다.
@Transactional
class AuthIntegrationTest extends PostgresContainerSupport {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

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
        // 기대 요청은 테스트마다 새로 등록한다. 남겨 두면 앞 테스트의 기대가 뒤에 영향을 준다.
        kakaoServer.reset();
    }

    private void expectKakaoCall(String responseBody) {
        expectKakaoCalls(1, responseBody);
    }

    /**
     * 카카오 호출을 몇 번 기대하는지 미리 등록한다.
     *
     * <p>가짜 서버는 <b>첫 요청이 나간 뒤에는 기대를 더 등록할 수 없다.</b> 그래서 한 테스트에서
     * 두 번 로그인한다면 두 번 분을 미리 걸어 둬야 한다.
     */
    private void expectKakaoCalls(int count, String responseBody) {
        kakaoServer.expect(ExpectedCount.times(count), requestTo(StubbedSocialServerConfig.KAKAO_USER_INFO_URI))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private MvcResult performLogin(String provider) throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(provider)))
                .andReturn();
    }

    private String loginBody(String provider) {
        return """
                {"provider": "%s", "providerToken": "valid-kakao-token"}
                """.formatted(provider);
    }

    private MvcResult loginSuccessfully() throws Exception {
        expectKakaoCall(KAKAO_RESPONSE);
        MvcResult result = performLogin("kakao");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result;
    }

    /** 응답 JSON에서 값을 하나 꺼낸다. */
    private String read(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    // ---------- 신규 가입 / 기존 로그인 ----------

    @Test
    @DisplayName("처음 로그인하면 회원과 소셜 계정이 실제로 저장된다")
    void firstLoginPersistsUser() throws Exception {
        long before = userRepository.count();

        expectKakaoCall(KAKAO_RESPONSE);
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("kakao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.isNewUser").value(true))
                .andExpect(jsonPath("$.data.user.nickname").value("감자러버"));

        assertThat(userRepository.count()).isEqualTo(before + 1);
        assertThat(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.KAKAO, "987654321"))
                .isPresent();
    }

    @Test
    @DisplayName("같은 계정으로 다시 로그인하면 회원이 늘지 않고 isNewUser=false다")
    void secondLoginReusesUser() throws Exception {
        expectKakaoCalls(2, KAKAO_RESPONSE);

        performLogin("kakao");
        long afterFirst = userRepository.count();

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("kakao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.isNewUser").value(false));

        // 같은 소셜 계정으로 회원이 둘 생기면 로그인할 때마다 다른 사람이 된다.
        assertThat(userRepository.count()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("이메일 동의를 받지 못한 계정도 가입된다")
    void signsUpWithoutEmail() throws Exception {
        // 애플의 이메일 가리기(Private Relay)와 같은 상황. 이메일 없이도 서비스를 쓸 수 있어야 한다.
        expectKakaoCall("""
                {"id": 555555555, "kakao_account": {"profile": {"nickname": "익명감자"}}}
                """);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("kakao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.isNewUser").value(true));

        assertThat(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.KAKAO, "555555555"))
                .isPresent()
                .get()
                .satisfies(socialAuth -> assertThat(socialAuth.getUser().getEmail()).isNull());
    }

    // ---------- 로그인 실패 ----------

    @Test
    @DisplayName("카카오가 토큰을 거절하면 401이고 회원은 생기지 않는다")
    void invalidProviderTokenCreatesNothing() throws Exception {
        long before = userRepository.count();
        kakaoServer.expect(requestTo(StubbedSocialServerConfig.KAKAO_USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("kakao")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));

        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("카카오 서버 장애면 503이다")
    void socialOutageReturnsServiceUnavailable() throws Exception {
        kakaoServer.expect(requestTo(StubbedSocialServerConfig.KAKAO_USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("kakao")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("COMMON_503"));
    }

    @Test
    @DisplayName("애플은 구현체가 없어 400이다")
    void appleIsNotAvailableYet() throws Exception {
        // 개발자 계정 미보유로 보류 중이다. (roadmap 4-4-3) 500이 아니라 400으로 거절돼야 한다.
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("apple")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("구글은 클라이언트 ID 미설정이라 400이다")
    void googleIsNotConfiguredYet() throws Exception {
        // aud를 검증할 수 없으면 남의 앱 토큰이 통과할 수 있으므로 아예 막는다. (4-4-2)
        // GOOGLE_AUDIENCES가 설정되면 이 테스트는 바뀐다.
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("google")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    // ---------- 발급된 토큰이 실제로 통하는가 ----------

    @Test
    @DisplayName("로그인 → 액세스 토큰으로 보호된 API 호출 → 로그아웃까지 이어진다")
    void tokenFromLoginWorksOnProtectedApi() throws Exception {
        MvcResult login = loginSuccessfully();
        String accessToken = read(login, "$.data.accessToken");

        mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("로그아웃 완료"));
    }

    @Test
    @DisplayName("로그인 → 토큰 갱신 → 갱신된 토큰으로도 보호된 API가 통한다")
    void refreshedTokenWorks() throws Exception {
        MvcResult login = loginSuccessfully();
        String refreshToken = read(login, "$.data.refreshToken");

        MvcResult refreshed = mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post(LOGOUT_PATH)
                        .header("Authorization", "Bearer " + read(refreshed, "$.data.accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃한 뒤에도 토큰은 만료 전까지 유효하다")
    void tokenStaysValidAfterLogout() throws Exception {
        // stateless 구조의 알려진 성질이다. 서버가 토큰을 무효화하지 못하므로 앱이 지워야 한다.
        // 나중에 블랙리스트를 도입하면 이 테스트가 실패하고, 그때 기대를 바꾸면 된다.
        MvcResult login = loginSuccessfully();
        String accessToken = read(login, "$.data.accessToken");

        mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    // ---------- 인증이 필요한 경로 ----------

    @Test
    @DisplayName("토큰 없이 로그아웃하면 401 COMMON_401")
    void logoutWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(LOGOUT_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("위조된 토큰으로 로그아웃하면 401")
    void logoutWithForgedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("리프레시 토큰으로는 보호된 API를 쓸 수 없다")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        // 둘은 형태가 같고 둘 다 우리가 서명한 진짜다. 용도(type 클레임)로만 구분된다. (4-1)
        MvcResult login = loginSuccessfully();
        String refreshToken = read(login, "$.data.refreshToken");

        mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰으로 갱신하면 401 AUTH_002")
    void refreshWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("액세스 토큰으로는 갱신할 수 없다")
    void accessTokenCannotBeUsedForRefresh() throws Exception {
        MvcResult login = loginSuccessfully();
        String accessToken = read(login, "$.data.accessToken");

        mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }
}
