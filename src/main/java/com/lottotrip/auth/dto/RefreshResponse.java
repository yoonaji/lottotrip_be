package com.lottotrip.auth.dto;

/**
 * 토큰 갱신 응답. (tour_api_erd.md 4-1)
 *
 * 액세스 토큰만 돌려준다. 리프레시 토큰은 새로 만들지 않으므로 응답에 넣지 않는다.
 * (앱이 갖고 있는 것을 만료될 때까지 계속 쓴다.)
 */
public record RefreshResponse(String accessToken) {
}
