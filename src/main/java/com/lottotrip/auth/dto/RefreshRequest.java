package com.lottotrip.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 갱신 요청. (tour_api_erd.md 4-1)
 *
 * 액세스 토큰은 헤더로 보내는데 리프레시 토큰은 본문으로 받는다. 명세가 그렇게 정해져 있고,
 * 용도가 다르기 때문이기도 하다.
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
