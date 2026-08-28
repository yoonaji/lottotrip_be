package com.lottotrip.route.tmap;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T맵(SK Open API) 보행자 경로안내 담당. 구조는 {@link com.lottotrip.route.odsay.OdsayClient}와 같지만
 * 인증·요청 방식이 다르다 — GET+쿼리파라미터가 아니라 **POST + JSON 바디**이고,
 * 인증키는 헤더 하나(`appKey`)로만 싣는다.
 *
 * ⚠️ 4xx 응답의 본문 구조(에러 코드 체계)가 공식 문서에 명시돼 있지 않아, ODsay·NCP처럼
 * "진짜 경로 없음"과 "인증 오류"를 코드로 구분하지 못한다. 우선 TourApiClient처럼 4xx/5xx를
 * 전부 SERVICE_UNAVAILABLE로 묶어 두고, 실제 호출로 에러 응답 모양이 확인되면 ODsay/NCP 때처럼
 * 다시 나눈다.
 */
@Slf4j
@Component
public class TmapPedestrianClient {

    private static final String PATH = "/tmap/routes/pedestrian";
    private static final String API_VERSION = "1";

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
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        log.warn("T맵 보행자 경로 오류 응답: status={}, uri={}", res.getStatusCode(), uri);
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(TmapPedestrianResponse.class);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("T맵 호출 실패 uri={}: {}", uri, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("T맵 인증키가 없습니다. 환경변수 TMAP_APP_KEY를 확인하세요.");
        }
    }
}
