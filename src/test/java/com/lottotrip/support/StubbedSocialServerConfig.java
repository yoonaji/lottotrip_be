package com.lottotrip.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 소셜 서버(카카오)만 가짜로 바꾸는 테스트 설정.
 *
 * `RestClient.Builder`를 `@Primary`로 덮어써서, 진짜 `KakaoTokenVerifier`가
 * 이 가짜 통로를 쓰게 만든다. **검증기 자체는 진짜 코드가 그대로 돈다** — 바꾸는 것은
 * "바깥으로 나가는 길"뿐이다.
 *
 * 둘을 한 객체({@link Stub}) 안에서 함께 만드는 이유는 **순서** 때문이다. 검증기가 builder로
 * `build()`를 부르기 전에 가짜 연결이 꽂혀 있어야 한다. 따로 만들면 검증기가 먼저 생겨
 * 진짜 카카오로 요청이 나갈 수 있다.
 *
 * 원래 `AuthIntegrationTest` 안에 있던 것을 9-1에서 밖으로 뺐다. 전체 시나리오 테스트도
 * 로그인부터 시작하므로 같은 설정이 필요한데, 복사해 두면 한쪽만 고쳐져 어긋난다.
 */
@TestConfiguration
public class StubbedSocialServerConfig {

    /** 카카오 사용자 정보 조회 주소. 기대 요청을 걸 때 쓴다. */
    public static final String KAKAO_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    /** 카카오 토큰 정보 조회 주소. 토큰이 어느 앱 것인지 확인하는 곳이다. */
    public static final String KAKAO_TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";

    /**
     * 테스트에서 쓰는 우리 앱의 카카오 앱 ID.
     *
     * 통합 테스트는 `src/test/resources`가 없어서 운영 `application.yml`을 그대로 읽는데,
     * 거기서는 `KAKAO_APP_ID` 환경변수가 없으면 비어 있다. 앱 ID가 없으면 검증기가
     * 카카오 로그인을 아예 거절하므로, 테스트 클래스에서 이 값을 프로퍼티로 넣어 준다.
     */
    public static final long KAKAO_APP_ID = 1234567L;

    /** 우리 앱을 위해 발급된 토큰이라는 응답. */
    public static final String KAKAO_TOKEN_INFO_RESPONSE = """
            {"id": 987654321, "expires_in": 21599, "app_id": %d}
            """.formatted(KAKAO_APP_ID);

    public static class Stub {
        final RestClient.Builder builder = RestClient.builder();

        /**
         * 순서를 따지지 않게 한 이유.
         *
         * 로그인 한 번에 카카오를 두 번 부른다(토큰 정보 → 사용자 정보). 테스트마다 로그인 횟수가
         * 다르고 중간에 다른 호출이 끼기도 해서, 순서까지 맞추려면 테스트가 실제 동작이 아니라
         * 호출 순서에 묶인다. 순서 자체는 {@code KakaoTokenVerifierTest}가 따로 고정한다.
         */
        final MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    }

    @Bean
    Stub socialServerStub() {
        return new Stub();
    }

    @Bean
    @Primary
    RestClient.Builder stubbedRestClientBuilder(Stub stub) {
        return stub.builder;
    }

    @Bean
    MockRestServiceServer stubbedSocialServer(Stub stub) {
        return stub.server;
    }

    /**
     * 토큰 정보 조회는 로그인마다 무조건 한 번씩 나간다. 테스트가 신경 쓸 대상이 아니라
     * 통과시켜 두기만 하면 되므로, 각 테스트의 `reset()` 직후 이걸 한 번 걸어 둔다.
     */
    public static void expectValidTokenInfo(MockRestServiceServer server) {
        server.expect(ExpectedCount.manyTimes(), requestTo(KAKAO_TOKEN_INFO_URI))
                .andRespond(withSuccess(KAKAO_TOKEN_INFO_RESPONSE, MediaType.APPLICATION_JSON));
    }
}
