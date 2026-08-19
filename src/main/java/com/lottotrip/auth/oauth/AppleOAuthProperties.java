package com.lottotrip.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 애플 연동 설정. (application.yml의 `oauth.apple`)
 *
 * ⚠️ 팀 ID·키 ID·`.p8`는 여기 없다. 그 셋은 서버가 애플에 토큰을 요청할 때 쓰는 값이고,
 * 검증에는 공개키와 `aud`만 있으면 된다. 탈퇴 시 연동 해제(revoke)를 붙이면 그때 필요해진다.
 *
 * @param jwksUri   애플 공개키 목록 주소. 이 키로 identity token의 서명을 검증한다
 * @param audiences 우리 앱의 번들 ID(웹이면 Service ID) 목록. 토큰의 `aud`가 이 중 하나여야 한다
 */
@ConfigurationProperties(prefix = "oauth.apple")
public record AppleOAuthProperties(String jwksUri, List<String> audiences) {

    /** 설정이 비면 들어오는 null·빈 문자열을 걸러 낸다. 그대로 두면 `aud`가 빈 토큰이 통과한다. */
    public AppleOAuthProperties {
        audiences = audiences == null
                ? List.of()
                : audiences.stream().filter(a -> a != null && !a.isBlank()).toList();
    }
}
