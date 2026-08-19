package com.lottotrip.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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

    public static class Stub {
        final RestClient.Builder builder = RestClient.builder();
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
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
}
