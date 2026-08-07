package com.lottotrip.auth.entity;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;

import java.util.Arrays;

/**
 * 소셜 로그인 제공자. (tour_api_erd.md 결정 4 — 카카오 · 애플 · 구글 3종)
 *
 * <p>ERD의 {@code oauth_provider} enum에 대응한다.
 * 4단계의 provider별 토큰 검증 구현체를 고르는 기준으로도 쓰인다.
 */
public enum ProviderType {
    KAKAO,
    APPLE,
    GOOGLE;

    /**
     * 요청 본문의 문자열({@code "kakao"})을 provider로 바꾼다.
     *
     * <p>DB 저장값은 대문자({@code KAKAO})인데 API 명세의 요청은 소문자다. (tour_api_erd.md 4-1)
     * 대소문자를 무시해 받으면 양쪽을 모두 수용할 수 있다. {@code TransportType.from()}과 같은 방식이다.
     *
     * <p>정의되지 않은 값이면 400으로 거절한다. 여기서 걸러 내지 않으면 "구현체를 찾을 수 없다"는
     * 다른 이유로 실패해, 잘못 보낸 쪽이 원인을 알기 어렵다.
     */
    public static ProviderType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }
}
