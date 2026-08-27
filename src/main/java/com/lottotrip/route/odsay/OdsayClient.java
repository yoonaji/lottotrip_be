package com.lottotrip.route.odsay;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * ODsay Lab 대중교통(버스+지하철) 경로 조회 담당.
 *
 * 구조는 {@code TourApiClient}와 같다 — `RestClient`로 부르고, 실패는 {@link CustomException}으로 모은다.
 * 인증키에도 `+` `/` `=`가 섞여 있을 수 있어(우리 발급키에 `/`가 실제로 들어 있다),
 * URL 조립 방식(한 번만 인코딩 + {@code build(true)}로 재인코딩 방지)도 그대로 따른다.
 */
@Slf4j
@Component
public class OdsayClient {

    private static final String OP_SEARCH_PUB_TRANS_PATH = "searchPubTransPathT";

    /** 추천 경로 순으로 받는다(0). 유형별 정렬(1)은 지금 쓰지 않는다. */
    private static final int OPT_RECOMMENDED = 0;

    /** 한국어 응답. */
    private static final int LANG_KOREAN = 0;

    /**
     * "경로가 실제로 없음"을 뜻하는 코드. -99=검색결과 없음, -98=출발·도착 700m 이내,
     * 3/4/5=정류장 없음, 6=서비스 지역 아님. 인증·형식 오류(-8, -9 등)와는 성격이 달라서
     * 이 코드들만 {@link ErrorCode#ROUTE_NOT_FOUND}(404)로 보내고 나머지는
     * {@link ErrorCode#SERVICE_UNAVAILABLE}로 묶는다.
     */
    private static final Set<String> NO_ROUTE_ERROR_CODES = Set.of("-99", "-98", "3", "4", "5", "6");

    private final RestClient restClient;
    private final OdsayProperties properties;

    public OdsayClient(RestClient.Builder restClientBuilder, OdsayProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        if (!properties.isConfigured()) {
            log.warn("ODsay 인증키(ODSAY_API_KEY)가 설정되지 않아 대중교통 경로 조회가 비활성 상태입니다.");
        }
    }

    /**
     * 출발지에서 도착지까지의 추천 대중교통 경로 1건.
     *
     * `x`에 경도, `y`에 위도를 넣는다. TourAPI의 mapX/mapY와 같은 함정이라
     * 뒤집어 보내면 오류 없이 엉뚱한(또는 0건인) 결과가 조용히 온다.
     *
     * @param startLongitude 출발 경도
     * @param startLatitude  출발 위도
     * @param endLongitude   도착 경도
     * @param endLatitude    도착 위도
     * @throws CustomException 경로가 없으면 {@link ErrorCode#ROUTE_NOT_FOUND}, 그 외 실패는
     *                          {@link ErrorCode#SERVICE_UNAVAILABLE}
     */
    public OdsayResponse.Path findRecommendedRoute(
            double startLongitude, double startLatitude, double endLongitude, double endLatitude) {
        URI uri = uri(startLongitude, startLatitude, endLongitude, endLatitude);
        OdsayResponse response = call(uri);

        List<OdsayResponse.Path> paths = response.paths();
        if (paths.isEmpty()) {
            throw new CustomException(ErrorCode.ROUTE_NOT_FOUND);
        }
        return paths.get(0);
    }

    // ---------- 호출 ----------

    private OdsayResponse call(URI uri) {
        try {
            OdsayResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        log.warn("ODsay 오류 응답: status={}, uri={}", res.getStatusCode(), maskApiKey(uri));
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(OdsayResponse.class);

            return validate(response, uri);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("ODsay 호출 실패 uri={}: {}", maskApiKey(uri), e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** HTTP는 200인데 본문에 {@code error}가 실려 오는 경우를 걸러낸다. */
    private OdsayResponse validate(OdsayResponse response, URI uri) {
        if (response == null) {
            log.warn("ODsay 응답 본문이 비어 있음: uri={}", maskApiKey(uri));
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (response.error() != null) {
            String code = response.error().code();
            if (NO_ROUTE_ERROR_CODES.contains(code)) {
                log.debug("ODsay 경로 없음: code={}, message={}", code, response.error().message());
                throw new CustomException(ErrorCode.ROUTE_NOT_FOUND);
            }
            log.warn("ODsay 오류 코드: code={}, message={}, uri={}",
                    code, response.error().message(), maskApiKey(uri));
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return response;
    }

    // ---------- URL 조립 ----------

    private URI uri(double startLng, double startLat, double endLng, double endLat) {
        requireConfigured();

        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/" + OP_SEARCH_PUB_TRANS_PATH)
                .queryParam("apiKey", URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8))
                .queryParam("SX", startLng)
                .queryParam("SY", startLat)
                .queryParam("EX", endLng)
                .queryParam("EY", endLat)
                .queryParam("OPT", OPT_RECOMMENDED)
                .queryParam("lang", LANG_KOREAN)
                .queryParam("output", "json")
                .build(true)
                .toUri();
    }

    /**
     * 인증키가 없으면 네트워크를 타기 전에 멈춘다. 사용자 요청 처리 중 생긴 문제가 아니라
     * 서버 설정 실수이므로 {@link CustomException}이 아니라 {@link IllegalStateException}이다.
     */
    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("ODsay 인증키가 없습니다. 환경변수 ODSAY_API_KEY를 확인하세요.");
        }
    }

    static String maskApiKey(String url) {
        return url == null ? null : url.replaceAll("(?i)(apiKey=)[^&]*", "$1***");
    }

    private static String maskApiKey(URI uri) {
        return maskApiKey(uri.toString());
    }
}
