package com.lottotrip.route.odsay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ODsay Lab 접속 설정.
 *
 * {@code TourApiProperties}와 같은 이유로 값을 코드에 박지 않고 여기로 뺐다 — 인증키를
 * 소스에 적으면 저장소에 그대로 올라간다.
 *
 * @param baseUrl 대중교통 경로 조회 API 주소
 * @param apiKey  ODsay 콘솔에서 발급한 인증키. 콘솔의 "서비스 플랫폼 환경"에 우리 서버의
 *                공인 IP가 Server로 등록돼 있어야 이 키로 호출이 통과한다.
 */
@ConfigurationProperties(prefix = "odsay")
public record OdsayProperties(String baseUrl, String apiKey) {

    private static final String DEFAULT_BASE_URL = "https://api.odsay.com/v1/api";

    public OdsayProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
    }

    /** 인증키가 채워져 있는지. 비어 있으면 호출해 봐야 인증 오류만 돌아온다. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
