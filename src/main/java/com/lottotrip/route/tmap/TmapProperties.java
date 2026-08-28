package com.lottotrip.route.tmap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T맵(SK Open API) 보행자 경로안내 접속 설정.
 *
 * @param baseUrl 도보 경로안내 API 주소
 * @param appKey  openapi.sk.com 콘솔의 애플리케이션에서 발급받는 키. 요청 헤더 {@code appKey}로 싣는다
 */
@ConfigurationProperties(prefix = "tmap")
public record TmapProperties(String baseUrl, String appKey) {

    private static final String DEFAULT_BASE_URL = "https://apis.openapi.sk.com";

    public TmapProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
    }

    /** 인증키가 채워져 있는지. 비어 있으면 호출해 봐야 인증 오류만 돌아온다. */
    public boolean isConfigured() {
        return appKey != null && !appKey.isBlank();
    }
}
