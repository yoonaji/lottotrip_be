package com.lottotrip.route.navermap;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * NCP Maps Directions 5(자동차 길찾기) 담당. 구조는 {@link com.lottotrip.route.odsay.OdsayClient}와 같다.
 *
 * ⚠️ 인증키가 URL이 아니라 헤더로 들어가므로, ODsay·TourAPI에 있던 "URL에 실린 키를 로그에서
 * 가리는" 처리가 필요 없다 — 키가 애초에 URL에 실리지 않는다.
 *
 * ⚠️ 실패 응답의 {@code code} 값별 의미(인증 오류 vs 경로 없음)는 문서 기준의 가정이다.
 * 인증키를 발급받으면 실제 호출 1회로 대조하는 절차가 필요하다 — ODsay 때
 * {@code totalDistance}가 실수로 온다는 걸 실측 전엔 몰랐던 것과 같은 이유다.
 */
@Slf4j
@Component
public class NaverDirectionsClient {

    private static final String OP_DRIVING = "driving";

    /** 최단시간 우선 경로. 다른 옵션(tracomfort·traoptimal)은 지금 쓰지 않는다. */
    private static final String OPTION_FASTEST = "trafast";

    private final RestClient restClient;
    private final NaverDirectionsProperties properties;

    public NaverDirectionsClient(RestClient.Builder restClientBuilder, NaverDirectionsProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        if (!properties.isConfigured()) {
            log.warn("네이버 지도 인증키(NAVER_MAP_API_KEY_ID/NAVER_MAP_API_KEY)가 설정되지 않아 "
                    + "자동차 경로 조회가 비활성 상태입니다.");
        }
    }

    /**
     * 출발지에서 도착지까지의 최단시간 자동차 경로 1건.
     *
     * `x`에 경도, `y`에 위도를 넣는다 — TourAPI·ODsay와 같은 함정.
     */
    public NaverDirectionsResponse.TrafastRoute findFastestRoute(
            double startLongitude, double startLatitude, double endLongitude, double endLatitude) {
        requireConfigured();

        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/" + OP_DRIVING)
                .queryParam("start", startLongitude + "," + startLatitude)
                .queryParam("goal", endLongitude + "," + endLatitude)
                .queryParam("option", OPTION_FASTEST)
                .build()
                .toUri();

        NaverDirectionsResponse response = call(uri);

        List<NaverDirectionsResponse.TrafastRoute> routes = response.routes();
        if (routes.isEmpty()) {
            throw new CustomException(ErrorCode.ROUTE_NOT_FOUND);
        }
        return routes.get(0);
    }

    private NaverDirectionsResponse call(URI uri) {
        try {
            NaverDirectionsResponse response = restClient.get()
                    .uri(uri)
                    .header("x-ncp-apigw-api-key-id", properties.apiKeyId())
                    .header("x-ncp-apigw-api-key", properties.apiKey())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        log.warn("네이버 길찾기 오류 응답: status={}, uri={}", res.getStatusCode(), uri);
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(NaverDirectionsResponse.class);

            return validate(response, uri);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("네이버 길찾기 호출 실패 uri={}: {}", uri, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * HTTP는 200인데 본문의 {@code code}가 0이 아닌 경우를 걸러낸다.
     *
     * 인증·형식 오류는 NCP API 게이트웨이 단계에서 4xx/5xx로 걸러지고(위 onStatus),
     * 여기 오는 {@code code != 0}은 문서상 "경로 탐색 자체의 실패"(출발·도착 반경 밖,
     * 도로 없음 등)를 뜻한다고 가정해 ROUTE_NOT_FOUND로 보낸다.
     */
    private NaverDirectionsResponse validate(NaverDirectionsResponse response, URI uri) {
        if (response == null) {
            log.warn("네이버 길찾기 응답 본문이 비어 있음: uri={}", uri);
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (!response.isSuccess()) {
            log.debug("네이버 길찾기 경로 없음: code={}, message={}", response.code(), response.message());
            throw new CustomException(ErrorCode.ROUTE_NOT_FOUND);
        }
        return response;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "네이버 지도 인증키가 없습니다. 환경변수 NAVER_MAP_API_KEY_ID/NAVER_MAP_API_KEY를 확인하세요.");
        }
    }
}
