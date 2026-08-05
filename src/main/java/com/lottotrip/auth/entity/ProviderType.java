package com.lottotrip.auth.entity;

/**
 * 소셜 로그인 제공자. (tour_api_erd.md 결정 4 — 카카오 · 애플 · 구글 3종)
 *
 * <p>ERD의 {@code oauth_provider} enum에 대응한다.
 * 4단계의 provider별 토큰 검증 구현체를 고르는 기준으로도 쓰인다.
 */
public enum ProviderType {
    KAKAO,
    APPLE,
    GOOGLE
}
