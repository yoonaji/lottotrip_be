package com.lottotrip.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 연동 설정. (application.yml의 {@code oauth.kakao})
 *
 * <p>URL을 코드에 직접 박지 않고 설정으로 빼는 이유는, 테스트나 스테이징에서 다른 주소를
 * 가리켜야 할 때 코드를 고치지 않기 위해서다.
 *
 * @param userInfoUri 액세스 토큰으로 사용자 정보를 조회하는 카카오 API 주소
 */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoOAuthProperties(String userInfoUri) {
}
