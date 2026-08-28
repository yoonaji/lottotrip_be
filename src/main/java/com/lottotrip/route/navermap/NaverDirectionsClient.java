package com.lottotrip.route.navermap;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * NCP Maps Directions 5(자동차 길찾기) 담당. 구조는 {@link com.lottotrip.route.odsay.OdsayClient}와 같다.
 *
 * ⚠️ 인증키가 URL이 아니라 헤더로 들어가므로, ODsay·TourAPI에 있던 "URL에 실린 키를 로그에서
 * 가리는" 처리가 필요 없다 — 키가 애초에 URL에 실리지 않는다.
 *
 * ⚠️ 경로 탐색 실패는 ODsay(HTTP 200 + 본문 error)와 다르게 **HTTP 400**으로 온다(공식 문서 실측,
 * 2026-08-28). 그래서 5xx만 {@code onStatus}로 잡고, 4xx는 예외로 받은 뒤 본문을 다시 읽어
 * "진짜 경로 없음"과 "인증·형식 오류"를 구분한다.
 */
@Slf4j
@Component
public class NaverDirectionsClient {

    private static final String OP_DRIVING = "driving";

    /** 최단시간 우선 경로. 다른 옵션(tracomfort·traoptimal)은 지금 쓰지 않는다. */
    private static final String OPTION_FASTEST = "trafast";

    /**
     * 경로 탐색 자체의 실패를 뜻하는 공식 에러코드(문서 실측, 2026-08-28). 전부 HTTP 400으로 온다.
     * 1=출발·도착 동일, 2=출발/도착이 도로 주변 아님, 3=결과 제공 불가,
     * 4=경유지가 도로 주변 아님, 5=경유지 포함 직선거리 1500km 이상.
     */
    private static final Set<Integer> ROUTE_NOT_FOUND_CODES = Set.of(1, 2, 3, 4, 5);

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
                    .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                        log.warn("네이버 길찾기 서버 오류: status={}, uri={}", res.getStatusCode(), uri);
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(NaverDirectionsResponse.class);

            return validate(response, uri);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // 4xx는 위 onStatus가 안 잡으므로 여기로 넘어온다. 본문을 다시 읽어야
            // "경로가 없는 정상적인 실패"와 "키가 잘못된 진짜 오류"를 구분할 수 있다.
            throw mapClientError(e, uri);
        } catch (RestClientException e) {
            log.warn("네이버 길찾기 호출 실패 uri={}: {}", uri, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private CustomException mapClientError(RestClientResponseException e, URI uri) {
        NaverDirectionsResponse body = tryParseErrorBody(e);
        if (body != null && ROUTE_NOT_FOUND_CODES.contains(body.code())) {
            log.debug("네이버 길찾기 경로 없음: code={}, message={}", body.code(), body.message());
            return new CustomException(ErrorCode.ROUTE_NOT_FOUND);
        }
        log.warn("네이버 길찾기 오류 응답: status={}, uri={}, body={}",
                e.getStatusCode(), uri, e.getResponseBodyAsString());
        return new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    /**
     * 인증 실패 같은 진짜 오류는 우리 응답 형태와 다른(또는 빈) 본문으로 온다
     * (실측: errorCode 210 케이스는 {@code {"error": {...}}} 형태다). 파싱이 안 되면
     * "경로 없음"이 아니라는 뜻이므로 null로 돌려 SERVICE_UNAVAILABLE로 빠지게 한다.
     */
    private NaverDirectionsResponse tryParseErrorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(NaverDirectionsResponse.class);
        } catch (RuntimeException parseError) {
            log.debug("네이버 길찾기 오류 응답 본문 파싱 실패: {}", parseError.getMessage());
            return null;
        }
    }

    /** HTTP는 200인데 code가 0이 아닌 경우를 방어적으로 거른다. 문서상으로는 일어나지 않아야 한다. */
    private NaverDirectionsResponse validate(NaverDirectionsResponse response, URI uri) {
        if (response == null) {
            log.warn("네이버 길찾기 응답 본문이 비어 있음: uri={}", uri);
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (!response.isSuccess()) {
            log.warn("네이버 길찾기 200 응답인데 code!=0: code={}, message={}, uri={}",
                    response.code(), response.message(), uri);
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
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
