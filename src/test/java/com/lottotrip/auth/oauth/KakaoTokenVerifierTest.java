package com.lottotrip.auth.oauth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 토큰 검증 구현체 검증. (roadmap 4-4-1)
 *
 * 실제 카카오 서버를 부르지 않는다. {@link MockRestServiceServer}가 `RestClient` 내부의
 * "요청을 실제로 보내는 부품"을 가짜로 바꿔치기하기 때문에 네트워크를 아예 타지 않는다.
 *
 * 이렇게 하는 이유는 **실패 상황을 우리가 만들어야 하기 때문**이다. 실제 카카오 서버에
 * "지금 401을 주세요", "500을 주세요"라고 요청할 방법이 없다. 명세상 검증해야 할 것은
 * 성공보다 오히려 `INVALID_PROVIDER_TOKEN` 쪽이다. (tour_api_erd.md 4-1)
 *
 * ⚠️ 여기 적힌 응답 JSON은 **카카오 문서 기준으로 작성한 가정**이다. 실제 응답 모양이 다르면
 * 이 테스트는 통과해도 운영에서 깨진다. 실제 토큰으로 한 번 확인하는 절차가 4-5에 따로 필요하다.
 */
class KakaoTokenVerifierTest {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String VALID_TOKEN = "kakao-access-token";

    /** 우리 앱의 카카오 앱 ID. 이 값과 다른 앱에서 발급된 토큰은 거절해야 한다. */
    private static final long OUR_APP_ID = 1234567L;

    /** 공격자가 자기 카카오 앱을 등록해 받아 온 토큰이라고 가정한 값. */
    private static final long OTHER_APP_ID = 7654321L;

    /** 이메일·닉네임 동의를 모두 받은 정상 응답. */
    private static final String FULL_RESPONSE = """
            {
              "id": 123456789,
              "connected_at": "2026-08-07T03:00:00Z",
              "kakao_account": {
                "email": "potato@example.com",
                "profile": {
                  "nickname": "감자러버",
                  "profile_image_url": "https://img.kakao.com/potato.png"
                }
              }
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private KakaoTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        // bindTo가 builder에 가짜 요청 부품을 꽂아 둔다. 이후 builder로 만든 RestClient는 전부 가짜를 쓴다.
        mockServer = MockRestServiceServer.bindTo(builder).build();
        verifier = newVerifier(OUR_APP_ID);
    }

    private KakaoTokenVerifier newVerifier(Long appId) {
        return new KakaoTokenVerifier(builder, new KakaoOAuthProperties(USER_INFO_URI, TOKEN_INFO_URI, appId));
    }

    /**
     * 토큰 정보 조회가 성공했다고 가정하고 응답을 걸어 둔다.
     *
     * 검증기는 토큰 정보를 먼저 보고 사용자 정보를 나중에 부른다. 가짜 서버는 등록한 순서대로
     * 요청이 오기를 기대하므로, 여기서 먼저 걸어 둔 뒤에 사용자 정보 기대를 등록해야 한다.
     */
    private void expectTokenInfo(long appId) {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess(tokenInfoBody(appId), MediaType.APPLICATION_JSON));
    }

    private String tokenInfoBody(long appId) {
        return """
                {"id": 123456789, "expires_in": 21599, "app_id": %d}
                """.formatted(appId);
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("정상 토큰이면 카카오 사용자 정보를 OAuthUserInfo로 변환한다")
    void returnsUserInfoOnValidToken() {
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(FULL_RESPONSE, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = verifier.verify(VALID_TOKEN);

        // 카카오 id는 숫자지만 providerUserId는 문자열이다(애플·구글은 문자열을 준다).
        assertThat(info.providerUserId()).isEqualTo("123456789");
        assertThat(info.email()).isEqualTo("potato@example.com");
        assertThat(info.nickname()).isEqualTo("감자러버");
        assertThat(info.profileImageUrl()).isEqualTo("https://img.kakao.com/potato.png");
        mockServer.verify();
    }

    @Test
    @DisplayName("액세스 토큰을 Authorization 헤더에 Bearer로 실어 보낸다")
    void sendsBearerToken() {
        // 실제 서버는 200/401만 돌려줄 뿐 "네 요청이 이렇게 생겼다"고 알려주지 않는다.
        // 우리가 무엇을 보냈는지 검증할 수 있는 것이 가짜 서버의 장점이다.
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andExpect(header("Authorization", "Bearer " + VALID_TOKEN))
                .andRespond(withSuccess(FULL_RESPONSE, MediaType.APPLICATION_JSON));

        verifier.verify(VALID_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("이메일 동의를 받지 못했으면 이메일 없이 통과한다")
    void allowsMissingEmail() {
        // 카카오는 사용자가 동의하지 않은 항목을 아예 응답에서 뺀다. 이것은 정상 상황이다.
        String noEmail = """
                {
                  "id": 123456789,
                  "kakao_account": { "profile": { "nickname": "감자러버" } }
                }
                """;
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(noEmail, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = verifier.verify(VALID_TOKEN);

        assertThat(info.providerUserId()).isEqualTo("123456789");
        assertThat(info.email()).isNull();
        assertThat(info.nickname()).isEqualTo("감자러버");
    }

    @Test
    @DisplayName("kakao_account 자체가 없어도 id만 있으면 통과한다")
    void allowsMissingAccountBlock() {
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"id\": 123456789}", MediaType.APPLICATION_JSON));

        OAuthUserInfo info = verifier.verify(VALID_TOKEN);

        assertThat(info.providerUserId()).isEqualTo("123456789");
        assertThat(info.email()).isNull();
        assertThat(info.nickname()).isNull();
        assertThat(info.profileImageUrl()).isNull();
    }

    // ---------- 토큰이 잘못된 경우 ----------

    @Test
    @DisplayName("카카오가 401을 주면 INVALID_PROVIDER_TOKEN")
    void mapsUnauthorizedToInvalidProviderToken() {
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"msg\":\"this access token does not exist\",\"code\":-401}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("expired-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("카카오가 400을 주면 INVALID_PROVIDER_TOKEN")
    void mapsBadRequestToInvalidProviderToken() {
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"msg\":\"invalid token\",\"code\":-2}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("garbage"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("id 없는 응답이면 INVALID_PROVIDER_TOKEN")
    void rejectsResponseWithoutId() {
        // 200이지만 사용자 식별자가 없다. 이대로 두면 provider_user_id가 빈 유령 회원이 생긴다.
        // 부모(SocialTokenVerifier)의 사후 검사가 걸러낸다.
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"kakao_account\": {}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("빈 토큰이면 카카오를 호출하지 않는다")
    void doesNotCallKakaoOnBlankToken() {
        // 기대 요청을 하나도 등록하지 않았다. 호출이 나가면 "예상하지 못한 요청"으로 실패한다.
        assertThatThrownBy(() -> verifier.verify("  "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);

        mockServer.verify();
    }

    // ---------- 카카오 쪽 장애 ----------

    @Test
    @DisplayName("카카오가 500을 주면 SERVICE_UNAVAILABLE (토큰 탓이 아니다)")
    void mapsServerErrorToServiceUnavailable() {
        // 카카오 점검 중에 401을 돌려주면 사용자는 "내 계정이 문제인가" 하고 로그인을 반복한다.
        // 우리 잘못도 사용자 잘못도 아니고 "지금은 안 된다"이므로 503이 맞다.
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("응답이 JSON이 아니면 SERVICE_UNAVAILABLE")
    void mapsBrokenBodyToServiceUnavailable() {
        // 점검 페이지(HTML)가 200으로 오는 경우가 실제로 있다. 파싱 실패는 토큰 문제가 아니다.
        expectTokenInfo(OUR_APP_ID);
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("<html>점검 중</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    // ---------- 앱 ID 대조 (다른 앱 토큰 차단) ----------

    @Test
    @DisplayName("다른 카카오 앱에서 발급된 토큰이면 INVALID_PROVIDER_TOKEN")
    void rejectsTokenIssuedToAnotherApp() {
        // 공격 시나리오다. 공격자가 카카오에 자기 앱을 등록하고 거기서 로그인하면
        // 카카오가 발급한 진짜 토큰을 얻는다. 서명도 만료도 멀쩡하고 사용자 정보 조회도 200이 온다.
        // 그 토큰이 우리 앱을 위해 발급된 것인지는 app_id를 봐야만 알 수 있다.
        expectTokenInfo(OTHER_APP_ID);

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("앱 ID가 다르면 사용자 정보는 조회하지 않는다")
    void doesNotFetchUserInfoWhenAppIdMismatches() {
        // 사용자 정보 기대를 등록하지 않았다. 호출이 나가면 "예상하지 못한 요청"으로 실패한다.
        // 어차피 거절할 토큰으로 카카오를 한 번 더 부를 이유가 없다.
        expectTokenInfo(OTHER_APP_ID);

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("app_id가 없는 응답이면 INVALID_PROVIDER_TOKEN")
    void rejectsTokenInfoWithoutAppId() {
        // 대조할 값이 없으면 통과시키지 않는다. 없는 것을 "같다"고 볼 수는 없다.
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\": 123456789}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("토큰 정보 조회에 액세스 토큰을 Bearer로 실어 보낸다")
    void sendsBearerTokenToTokenInfo() {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + VALID_TOKEN))
                .andRespond(withSuccess(tokenInfoBody(OUR_APP_ID), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(FULL_RESPONSE, MediaType.APPLICATION_JSON));

        verifier.verify(VALID_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("토큰 정보 조회가 401이면 INVALID_PROVIDER_TOKEN")
    void mapsTokenInfoUnauthorizedToInvalidProviderToken() {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"msg\":\"this access token does not exist\",\"code\":-401}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("expired-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("토큰 정보 조회가 500이면 SERVICE_UNAVAILABLE (토큰 탓이 아니다)")
    void mapsTokenInfoServerErrorToServiceUnavailable() {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    // ---------- 앱 ID 미설정 ----------

    @Test
    @DisplayName("앱 ID를 설정하지 않았으면 BAD_REQUEST로 거절한다")
    void rejectsWhenAppIdNotConfigured() {
        // 설정이 없다고 대조를 건너뛰면, 다른 앱의 토큰으로도 로그인할 수 있는 상태로 되돌아간다.
        // 검사를 못 하면 아예 받지 않는 쪽을 택한다. 구글이 클라이언트 ID 미설정 시
        // BAD_REQUEST를 주는 것과 같은 취급이다.
        KakaoTokenVerifier unconfigured = newVerifier(null);

        assertThatThrownBy(() -> unconfigured.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("앱 ID 미설정이면 카카오를 아예 호출하지 않는다")
    void doesNotCallKakaoWhenAppIdNotConfigured() {
        KakaoTokenVerifier unconfigured = newVerifier(null);

        assertThatThrownBy(() -> unconfigured.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class);

        mockServer.verify();
    }

    // ---------- 실제 카카오 응답으로 확인 (2026-08-21) ----------

    /**
     * 실제 카카오가 돌려준 `/v2/user/me` 응답. 카카오 REST API 테스트 도구로 받았다.
     *
     * 위쪽 `FULL_RESPONSE`는 문서를 보고 지어낸 것이라 실제와 다를 수 있었다. 이건 진짜다.
     * 개인정보라 닉네임과 회원번호만 바꿨고, 필드 구성은 받은 그대로 두었다.
     *
     * 눈여겨볼 것 두 가지.
     * - 문서에 없던 `for_partner`·`properties`·`has_age_range` 등이 잔뜩 딸려 온다
     * - 이메일 동의를 못 받으면 `email` 필드가 아예 빠진다 (`email_needs_agreement: true`)
     */
    private static final String REAL_USER_INFO_RESPONSE = """
            {
              "id": 5050573257,
              "connected_at": "2026-08-21T09:51:17Z",
              "for_partner": { "uuid": "VmdWYlBmU2FQaER2TntLf053R3dbalppUGUM" },
              "properties": {
                "nickname": "감자러버",
                "profile_image": "http://img1.kakaocdn.net/thumb/R640x640.q70/?fname=default.jpeg",
                "thumbnail_image": "http://img1.kakaocdn.net/thumb/R110x110.q70/?fname=default.jpeg"
              },
              "kakao_account": {
                "profile_needs_agreement": false,
                "profile": {
                  "nickname": "감자러버",
                  "thumbnail_image_url": "http://img1.kakaocdn.net/thumb/R110x110.q70/?fname=default.jpeg",
                  "profile_image_url": "http://img1.kakaocdn.net/thumb/R640x640.q70/?fname=default.jpeg",
                  "is_default_image": true,
                  "is_default_nickname": false
                },
                "has_email": true,
                "email_needs_agreement": true,
                "has_age_range": true,
                "age_range_needs_agreement": true,
                "has_birthday": true,
                "birthday_needs_agreement": true,
                "has_gender": true,
                "gender_needs_agreement": true
              }
            }
            """;

    /**
     * 실제 카카오가 돌려준 `/v1/user/access_token_info` 응답.
     *
     * `appId`·`expiresInMillis`는 테스트 도구가 보기 좋게 덧붙인 것으로 보인다. 서버 대 서버
     * 호출에서는 안 올 값이지만, 와도 무시되는지 확인하려고 일부러 그대로 남겨 두었다.
     */
    private static final String REAL_TOKEN_INFO_RESPONSE = """
            {
              "expiresInMillis": 21597652,
              "id": 5050573257,
              "expires_in": 21597,
              "app_id": %d,
              "appId": %d
            }
            """;

    @Test
    @DisplayName("실제 카카오 응답을 그대로 먹여도 매핑이 맞다")
    void parsesRealKakaoResponses() {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess(REAL_TOKEN_INFO_RESPONSE.formatted(OUR_APP_ID, OUR_APP_ID),
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(REAL_USER_INFO_RESPONSE, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = verifier.verify(VALID_TOKEN);

        // 회원번호가 Integer 최대값(2,147,483,647)을 넘는다. Long으로 받지 않으면 여기서 터진다.
        assertThat(info.providerUserId()).isEqualTo("5050573257");
        assertThat(info.nickname()).isEqualTo("감자러버");
        assertThat(info.profileImageUrl()).isEqualTo(
                "http://img1.kakaocdn.net/thumb/R640x640.q70/?fname=default.jpeg");
        // 이메일 동의를 못 받으면 카카오가 필드 자체를 빼고 준다. 로그인은 되고 이메일만 비어야 한다.
        assertThat(info.email()).isNull();
        mockServer.verify();
    }

    @Test
    @DisplayName("실제로 받아 본 다른 앱(10395)의 토큰은 거절한다")
    void rejectsRealTokenFromAnotherApp() {
        // 2026-08-21에 실제로 겪은 상황이다. 테스트 도구가 우리 앱이 아닌 앱으로 토큰을 발급했고,
        // 그 토큰은 카카오가 발급한 진짜였다. app_id를 안 봤다면 그대로 로그인됐을 토큰이다.
        long anotherApp = 10395L;
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess(REAL_TOKEN_INFO_RESPONSE.formatted(anotherApp, anotherApp),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);

        // 사용자 정보는 부르지 않는다.
        mockServer.verify();
    }

    // ---------- 구현체 식별 ----------

    @Test
    @DisplayName("담당 provider는 KAKAO다")
    void handlesKakao() {
        assertThat(verifier.getType()).isEqualTo(ProviderType.KAKAO);
    }
}
