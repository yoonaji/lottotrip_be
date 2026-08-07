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
 * <p>실제 카카오 서버를 부르지 않는다. {@link MockRestServiceServer}가 {@code RestClient} 내부의
 * "요청을 실제로 보내는 부품"을 가짜로 바꿔치기하기 때문에 네트워크를 아예 타지 않는다.
 *
 * <p>이렇게 하는 이유는 <b>실패 상황을 우리가 만들어야 하기 때문</b>이다. 실제 카카오 서버에
 * "지금 401을 주세요", "500을 주세요"라고 요청할 방법이 없다. 명세상 검증해야 할 것은
 * 성공보다 오히려 {@code INVALID_PROVIDER_TOKEN} 쪽이다. (tour_api_erd.md 4-1)
 *
 * <p>⚠️ 여기 적힌 응답 JSON은 <b>카카오 문서 기준으로 작성한 가정</b>이다. 실제 응답 모양이 다르면
 * 이 테스트는 통과해도 운영에서 깨진다. 실제 토큰으로 한 번 확인하는 절차가 4-5에 따로 필요하다.
 */
class KakaoTokenVerifierTest {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String VALID_TOKEN = "kakao-access-token";

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
        verifier = new KakaoTokenVerifier(builder, new KakaoOAuthProperties(USER_INFO_URI));
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("정상 토큰이면 카카오 사용자 정보를 OAuthUserInfo로 변환한다")
    void returnsUserInfoOnValidToken() {
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
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("<html>점검 중</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> verifier.verify(VALID_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    // ---------- 구현체 식별 ----------

    @Test
    @DisplayName("담당 provider는 KAKAO다")
    void handlesKakao() {
        assertThat(verifier.getType()).isEqualTo(ProviderType.KAKAO);
    }
}
