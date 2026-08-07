package com.lottotrip.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 갱신 요청. (tour_api_erd.md 4-1)
 *
 * <p>액세스 토큰은 헤더로 보내는데 리프레시 토큰은 <b>본문</b>으로 받는다. 명세가 그렇게 정해져 있고,
 * 용도가 다르기 때문이기도 하다. 액세스 토큰은 모든 API가 쓰는 신분증이지만 리프레시 토큰은
 * 이 API 하나에만 쓰이므로, 다른 요청에 실려 나갈 일이 없는 편이 안전하다.
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
