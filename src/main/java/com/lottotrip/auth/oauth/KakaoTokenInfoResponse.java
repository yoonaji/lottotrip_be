package com.lottotrip.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 토큰 정보 API(`GET /v1/user/access_token_info`)의 응답 모양.
 *
 * 이 API를 부르는 이유는 하나다. 받은 액세스 토큰이 우리 앱을 위해 발급된 것인지 확인하는 것.
 * 사용자 정보 조회(`/v2/user/me`)는 토큰이 유효한지만 알려줄 뿐, 어느 앱의 토큰인지는 알려주지 않는다.
 *
 * 응답에는 `expires_in`(만료까지 남은 초)도 오지만 쓰지 않는다. 만료된 토큰은 카카오가
 * 401로 거절하므로 우리가 남은 시간을 따로 볼 이유가 없다.
 *
 * @param id    회원번호. 카카오는 앱마다 다른 회원번호를 발급한다
 * @param appId 이 토큰이 발급된 앱의 ID
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTokenInfoResponse(
        Long id,
        @JsonProperty("app_id") Long appId
) {
}
