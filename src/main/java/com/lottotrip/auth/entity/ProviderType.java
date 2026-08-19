package com.lottotrip.auth.entity;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;

import java.util.Arrays;

/**
 * 소셜 로그인 제공자. (tour_api_erd.md 결정 4 — 카카오 · 애플 · 구글 3종)
 *
 * ERD의 `oauth_provider` enum에 대응한다.
 * 4단계의 provider별 토큰 검증 구현체를 고르는 기준으로도 쓰인다.
 */
public enum ProviderType {
    KAKAO,
    APPLE,
    GOOGLE;

    /**
     * 요청 본문의 문자열(`"kakao"`)을 provider로 바꾼다.
     *
     * DB 저장값은 대문자(`KAKAO`)인데 API 명세의 요청은 소문자다. (tour_api_erd.md 4-1)
     * 대소문자를 무시해 받으면 양쪽을 모두 수용한다.
     *
     * 정의되지 않은 값이면 400으로 거절한다.
     */
    public static ProviderType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }
}
