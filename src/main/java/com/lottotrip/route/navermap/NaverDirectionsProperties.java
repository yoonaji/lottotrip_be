package com.lottotrip.route.navermap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네이버 클라우드 플랫폼(NCP) Maps Directions 5 접속 설정.
 *
 * ⚠️ 인증키가 두 개(id·key)인 이유는 ODsay·TourAPI와 인증 방식 자체가 다르기 때문이다.
 * 이 API는 URL 쿼리가 아니라 요청 헤더(`x-ncp-apigw-api-key-id`, `x-ncp-apigw-api-key`)로
 * 인증한다.
 *
 * @param baseUrl Directions 5 API 주소
 * @param apiKeyId NCP 콘솔의 Maps 서비스에서 발급받는 값
 * @param apiKey   위와 짝을 이루는 값
 */
@ConfigurationProperties(prefix = "naver-map")
public record NaverDirectionsProperties(String baseUrl, String apiKeyId, String apiKey) {

    private static final String DEFAULT_BASE_URL = "https://naveropenapi.apigw.ntruss.com/map-direction/v1";

    public NaverDirectionsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
    }

    /** 두 인증키가 모두 채워져 있는지. 하나라도 비어 있으면 호출해 봐야 인증 오류만 돌아온다. */
    public boolean isConfigured() {
        return apiKeyId != null && !apiKeyId.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
