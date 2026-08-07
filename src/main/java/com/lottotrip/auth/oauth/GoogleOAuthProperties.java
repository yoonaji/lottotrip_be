package com.lottotrip.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 구글 연동 설정. (application.yml의 {@code oauth.google})
 *
 * @param jwksUri   구글 공개키 목록 주소. 이 키로 identity token의 서명을 검증한다
 * @param audiences 우리 앱의 구글 클라이언트 ID 목록. 토큰의 {@code aud}가 이 중 하나와 같아야 한다
 */
@ConfigurationProperties(prefix = "oauth.google")
public record GoogleOAuthProperties(String jwksUri, List<String> audiences) {

    /**
     * record의 <b>압축 생성자</b>. 값이 들어올 때 한 번 손보고 저장한다.
     *
     * <p>설정을 비워 두면 {@code null}이나 빈 문자열 하나짜리 목록이 들어올 수 있다.
     * 그대로 두면 {@code aud}가 빈 문자열인 토큰이 통과할 수 있으므로 여기서 걸러 낸다.
     */
    public GoogleOAuthProperties {
        audiences = audiences == null
                ? List.of()
                : audiences.stream().filter(a -> a != null && !a.isBlank()).toList();
    }
}
