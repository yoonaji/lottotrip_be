package com.lottotrip.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. (tour_api_erd.md 4-1)
 *
 * `@NotBlank`는 "null도 빈 문자열도 공백만 있는 것도 안 된다"는 뜻이다.
 * 컨트롤러에서 @Valid로 강제
 *
 * @param provider      `kakao` / `apple` / `google` (대소문자 무시)
 * @param providerToken 카카오는 access token, 애플·구글은 identity token
 */
public record LoginRequest(
        @NotBlank String provider,
        @NotBlank String providerToken
) {
}
