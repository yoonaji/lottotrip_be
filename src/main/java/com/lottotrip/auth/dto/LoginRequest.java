package com.lottotrip.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. (tour_api_erd.md 4-1)
 *
 * <p>{@code @NotBlank}는 "null도 빈 문자열도 공백만 있는 것도 안 된다"는 뜻이다.
 * 컨트롤러에서 {@code @Valid}를 붙이면 스프링이 <b>메서드에 들어오기 전에</b> 검사해 주므로,
 * 서비스 코드가 "값이 있는지" 확인하는 if문으로 채워지지 않는다.
 *
 * @param provider      {@code kakao} / {@code apple} / {@code google} (대소문자 무시)
 * @param providerToken 카카오는 access token, 애플·구글은 identity token
 */
public record LoginRequest(
        @NotBlank String provider,
        @NotBlank String providerToken
) {
}
