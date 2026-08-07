package com.lottotrip.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 사용자 정보 API({@code GET /v2/user/me})의 응답 모양.
 *
 * <p>이 클래스는 <b>카카오가 주는 JSON을 그대로 옮긴 것</b>이고, {@link OAuthUserInfo}는
 * 우리 서비스가 쓰는 공통 모양이다. 둘을 나누는 이유는 카카오가 응답 구조를 바꾸더라도
 * 고칠 곳이 여기 하나로 끝나게 하기 위해서다.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}는 "모르는 필드가 와도 무시하라"는 뜻이다.
 * 이게 없으면 카카오가 필드를 하나 추가하는 순간 <b>파싱이 통째로 실패해</b> 로그인이 막힌다.
 *
 * <p>{@code @JsonProperty}는 JSON의 이름과 자바 필드 이름을 연결한다. 카카오는
 * {@code kakao_account}처럼 밑줄 표기를 쓰고 자바는 {@code kakaoAccount}처럼 낙타 표기를 쓰기 때문이다.
 *
 * <p>동의하지 않은 항목은 카카오가 <b>응답에서 아예 뺀다.</b> 그래서 {@code kakaoAccount}·{@code profile}은
 * 언제든 {@code null}일 수 있고, 이를 안전하게 꺼내려고 아래 편의 메서드를 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            String email,
            Profile profile
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Profile(
                String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl
        ) {
        }
    }

    /** 카카오 id는 숫자지만 애플·구글은 문자열을 주므로, 공통 모양에 맞춰 문자열로 바꾼다. */
    public String providerUserId() {
        return id == null ? null : String.valueOf(id);
    }

    public String email() {
        return kakaoAccount == null ? null : kakaoAccount.email();
    }

    public String nickname() {
        return profile() == null ? null : profile().nickname();
    }

    public String profileImageUrl() {
        return profile() == null ? null : profile().profileImageUrl();
    }

    private KakaoAccount.Profile profile() {
        return kakaoAccount == null ? null : kakaoAccount.profile();
    }

    /** 카카오 응답을 서비스 공통 모양으로 번역한다. */
    public OAuthUserInfo toOAuthUserInfo() {
        return new OAuthUserInfo(providerUserId(), email(), nickname(), profileImageUrl());
    }
}
