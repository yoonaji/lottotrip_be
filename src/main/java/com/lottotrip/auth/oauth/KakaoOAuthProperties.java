package com.lottotrip.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 연동 설정. (application.yml의 `oauth.kakao`)
 *
 * URL을 코드에 직접 박지 않고 설정으로 빼는 이유는, 테스트나 스테이징에서 다른 주소를
 * 가리켜야 할 때 코드를 고치지 않기 위해서다.
 *
 * @param userInfoUri  액세스 토큰으로 사용자 정보를 조회하는 카카오 API 주소
 * @param tokenInfoUri 액세스 토큰이 어느 앱을 위해 발급됐는지 확인하는 카카오 API 주소
 * @param appId        우리 앱의 카카오 앱 ID. 토큰의 `app_id`가 이 값과 같아야 한다.
 *                     비밀값이 아니라 단순 식별자다. 설정하지 않으면 `null`이 들어온다
 */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoOAuthProperties(String userInfoUri, String tokenInfoUri, Long appId) {
}
