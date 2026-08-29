package com.lottotrip.route.tmap;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T맵(SK Open API) 보행자 경로안내 담당. 구조는 {@link com.lottotrip.route.odsay.OdsayClient}와 같지만
 * 인증·요청 방식이 다르다 — GET+쿼리파라미터가 아니라 **POST + JSON 바디**이고,
 * 인증키는 헤더 하나(`appKey`)로만 싣는다.
 *
 * 경로 탐색 실패는 NCP(HTTP 400 + 숫자 code)와 비슷하게 **HTTP 400** + {@code {"error":{...}}}
 * 바디로 오지만, 공식 문서에 코드 표가 없어 실제 발급받은 키로 호출해 실측했다(2026-08-29,
 * {@link TmapErrorResponse} 참고). 5xx만 {@code onStatus}로 잡고, 4xx는 예외로 받은 뒤 본문을
 * 다시 읽어 "진짜 경로 없음"과 "인증·요청 오류"를 구분한다.
 */
@Slf4j
@Component
public class TmapPedestrianClient {

    private static final String PATH = "/tmap/routes/pedestrian";
    private static final String API_VERSION = "1";

    /** API 로직 단계 거절(게이트웨이 아님)을 뜻하는 category. */
    private static final String CATEGORY_API = "tmap";

    /**
     * 경로 자체를 계산할 수 없다는 뜻의 공식 에러코드(실측, 2026-08-29). 전부 HTTP 400 +
     * {@code category=tmap}으로 온다. 1007=출발·도착이 너무 가까움(waypoints are too near),
     * 3102=좌표가 서비스 지원 구간 밖(도로 주변 아님, No service area).
     */
    private static final Set<String> ROUTE_NOT_FOUND_CODES = Set.of("1007", "3102");

    private final RestClient restClient;
    private final TmapProperties properties;

    public TmapPedestrianClient(RestClient.Builder restClientBuilder, TmapProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        if (!properties.isConfigured()) {
            log.warn("T맵 인증키(TMAP_APP_KEY)가 설정되지 않아 도보 경로 조회가 비활성 상태입니다.");
        }
    }

    /**
     * 출발지에서 도착지까지의 도보 경로 요약.
     *
     * `X`에 경도, `Y`에 위도를 넣는다 — TourAPI·ODsay·NCP와 같은 함정.
     * `startName`/`endName`은 필수 파라미터라 화면에 보여줄 일 없는 값이라도 채워 보낸다.
     */
    public TmapPedestrianResponse.Properties findRoute(
            double startLongitude, double startLatitude, double endLongitude, double endLatitude) {
        requireConfigured();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", startLongitude);
        body.put("startY", startLatitude);
        body.put("endX", endLongitude);
        body.put("endY", endLatitude);
        body.put("startName", "출발지");
        body.put("endName", "도착지");

        TmapPedestrianResponse response = call(body);

        List<TmapPedestrianResponse.Feature> features = response.featureList();
        return features.stream()
                .map(TmapPedestrianResponse.Feature::properties)
                .filter(p -> p != null && p.totalDistance() != null && p.totalTime() != null)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_NOT_FOUND));
    }

    private TmapPedestrianResponse call(Map<String, Object> body) {
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(PATH)
                .queryParam("version", API_VERSION)
                .build()
                .toUri();

        try {
            return restClient.post()
                    .uri(uri)
                    .header("appKey", properties.appKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                        log.warn("T맵 보행자 경로 서버 오류: status={}, uri={}", res.getStatusCode(), uri);
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(TmapPedestrianResponse.class);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // 4xx는 위 onStatus가 안 잡으므로 여기로 넘어온다. 본문을 다시 읽어야
            // "경로가 없는 정상적인 실패"와 "인증·요청이 잘못된 진짜 오류"를 구분할 수 있다.
            throw mapClientError(e, uri);
        } catch (RestClientException e) {
            log.warn("T맵 호출 실패 uri={}: {}", uri, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private CustomException mapClientError(RestClientResponseException e, URI uri) {
        TmapErrorResponse body = tryParseErrorBody(e);
        TmapErrorResponse.Error error = body == null ? null : body.error();
        if (error != null && CATEGORY_API.equals(error.category()) && ROUTE_NOT_FOUND_CODES.contains(error.code())) {
            log.debug("T맵 보행자 경로 없음: code={}, message={}", error.code(), error.message());
            return new CustomException(ErrorCode.ROUTE_NOT_FOUND);
        }
        log.warn("T맵 보행자 경로 오류 응답: status={}, uri={}, body={}",
                e.getStatusCode(), uri, e.getResponseBodyAsString());
        return new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    /**
     * 인증 실패(게이트웨이 단계, category=gw)는 우리 예상과 다른 code 체계라도 여기서
     * 파싱은 되지만, {@link #mapClientError}의 category/code 검사에서 걸러진다.
     * 아예 파싱이 안 되면(본문이 비었거나 JSON이 아니면) null로 돌려 SERVICE_UNAVAILABLE로 빠지게 한다.
     */
    private TmapErrorResponse tryParseErrorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(TmapErrorResponse.class);
        } catch (RuntimeException parseError) {
            log.debug("T맵 오류 응답 본문 파싱 실패: {}", parseError.getMessage());
            return null;
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("T맵 인증키가 없습니다. 환경변수 TMAP_APP_KEY를 확인하세요.");
        }
    }
}
