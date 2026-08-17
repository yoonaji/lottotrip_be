package com.lottotrip.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 애플 연동 설정. (application.yml의 `oauth.apple`)
 *
 * ⚠️ **팀 ID·키 ID·`.p8` 개인키는 여기 없다.** 그 셋은 서버가 애플에 직접 토큰을 요청할 때
 * (`client_secret`을 JWT로 만들어 서명할 때) 필요한 값이다. 우리는 **프론트가 받아 온
 * identity token을 검증만** 하므로 애플의 공개키와 `aud`만 있으면 된다.
 * 회원 탈퇴 시 애플 연동 해제(revoke)를 구현하게 되면 그때 필요해진다.
 *
 * @param jwksUri   애플 공개키 목록 주소. 이 키로 identity token의 서명을 검증한다
 * @param audiences 우리 앱의 **번들 ID**(웹이면 Service ID) 목록. 토큰의 `aud`가 이 중 하나여야 한다
 */
@ConfigurationProperties(prefix = "oauth.apple")
public record AppleOAuthProperties(String jwksUri, List<String> audiences) {

    /**
     * record의 압축 생성자. 설정이 비어 있을 때 들어오는 `null`이나 빈 문자열을 걸러 낸다.
     * 그대로 두면 `aud`가 빈 문자열인 토큰이 통과할 수 있다. ({@link GoogleOAuthProperties}와 같은 이유)
     */
    public AppleOAuthProperties {
        audiences = audiences == null
                ? List.of()
                : audiences.stream().filter(a -> a != null && !a.isBlank()).toList();
    }
}
