package com.lottotrip.route.tmap;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * T맵 보행자 경로안내 클라이언트 검증. {@code OdsayClientTest}와 같은 구조다.
 */
class TmapPedestrianClientTest {

    private static final String BASE_URL = "https://apis.openapi.sk.com";
    private static final String APP_KEY = "test-app-key";

    /** 요약이 첫 feature에만 실린다 — 실제 응답(2026-08-29 실측) 기준. */
    private static final String SUCCESS_RESPONSE = """
            {
              "type": "FeatureCollection",
              "features": [
                { "type": "Feature", "geometry": { "type": "Point", "coordinates": [127.1, 37.5] },
                  "properties": { "totalDistance": 632, "totalTime": 513, "index": 0 } },
                { "type": "Feature", "geometry": { "type": "LineString", "coordinates": [[127.1, 37.5]] },
                  "properties": { "index": 1, "description": "직진" } }
              ]
            }
            """;

    /** 실제로 받아온 응답이다(2026-08-29, 발급받은 키로 출발=도착에 가까운 좌표를 호출). */
    private static final String WAYPOINTS_TOO_NEAR_RESPONSE = """
            { "error": { "id": "400", "category": "tmap", "code": "1007",
                         "message": "사용할 수 없는 좌표계입니다. 사용 가능한 좌표계를 확인해주세요.([BadRequest]waypoints are too near. 100)" } }
            """;

    /** 실제로 받아온 응답이다(2026-08-29, 바다 한가운데처럼 도로 주변이 아닌 좌표로 호출). */
    private static final String NO_SERVICE_AREA_RESPONSE = """
            { "error": { "id": "400", "category": "tmap", "code": "3102",
                         "message": "해당 서비스가 지원되지 않는 구간입니다.([NoServiceArea]No service area for source)" } }
            """;

    /**
     * 실제로 받아온 응답이다(2026-08-29, 콘솔에 "보행자 경로안내" 상품이 추가되기 전 키로 호출).
     * category가 tmap이 아니라 gw(게이트웨이) — 우리 응답 스키마와 다른 인증 단계 거절이다.
     */
    private static final String INVALID_API_KEY_RESPONSE = """
            { "error": { "id": "403", "category": "gw", "code": "INVALID_API_KEY", "message": "Forbidden" } }
            """;

    /** 실제로 받아온 응답이다(2026-08-29, 요청 바디에 필수 파라미터를 빠뜨리고 호출). */
    private static final String MISSING_PARAMETER_RESPONSE = """
            { "error": { "id": "400", "category": "tmap", "code": "9401", "message": "필수 파라메터가 없습니다." } }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private TmapPedestrianClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TmapPedestrianClient(builder, properties(APP_KEY));
    }

    private TmapProperties properties(String appKey) {
        return new TmapProperties(BASE_URL, appKey);
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("첫 feature의 properties에서 거리·시간을 꺼낸다")
    void mapsSummaryFromFirstFeature() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        TmapPedestrianResponse.Properties properties = client.findRoute(127.1, 37.5, 127.2, 37.6);

        assertThat(properties.totalDistance()).isEqualTo(632);
        assertThat(properties.totalTime()).isEqualTo(513);
        mockServer.verify();
    }

    // ---------- 요청 형태 ----------

    @Test
    @DisplayName("POST로 /tmap/routes/pedestrian?version=1을 부른다")
    void sendsPostToVersionedPath() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian?version=1")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findRoute(127.1, 37.5, 127.2, 37.6);

        mockServer.verify();
    }

    @Test
    @DisplayName("인증키를 appKey 헤더로 싣는다")
    void sendsAppKeyAsHeader() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andExpect(header("appKey", APP_KEY))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findRoute(127.1, 37.5, 127.2, 37.6);

        mockServer.verify();
    }

    @Test
    @DisplayName("출발 경도·위도를 startX·startY에, 도착 경도·위도를 endX·endY에 JSON 바디로 싣는다")
    void sendsCoordinatesInRequestBody() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"startX\":127.1")))
                .andExpect(content().string(containsString("\"startY\":37.5")))
                .andExpect(content().string(containsString("\"endX\":127.2")))
                .andExpect(content().string(containsString("\"endY\":37.6")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findRoute(127.1, 37.5, 127.2, 37.6);

        mockServer.verify();
    }

    // ---------- 에러 ----------

    @Test
    @DisplayName("요약 feature가 없으면 ROUTE_NOT_FOUND")
    void failsWithRouteNotFoundWhenNoSummaryFeature() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withSuccess("""
                        { "type": "FeatureCollection", "features": [] }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findRoute(127.1, 37.5, 127.2, 37.6))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("서버가 5xx를 주면 SERVICE_UNAVAILABLE")
    void failsOnServerError() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.findRoute(127.1, 37.5, 127.2, 37.6))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("출발·도착이 너무 가까우면(code 1007) ROUTE_NOT_FOUND")
    void failsWithRouteNotFoundWhenWaypointsTooNear() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(WAYPOINTS_TOO_NEAR_RESPONSE));

        assertThatThrownBy(() -> client.findRoute(127.1, 37.5, 127.1, 37.5))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("좌표가 서비스 지원 구간 밖이면(code 3102) ROUTE_NOT_FOUND")
    void failsWithRouteNotFoundWhenNoServiceArea() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(NO_SERVICE_AREA_RESPONSE));

        assertThatThrownBy(() -> client.findRoute(130.0, 35.0, 130.01, 35.01))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("게이트웨이 인증 오류(category=gw)는 ROUTE_NOT_FOUND가 아니라 SERVICE_UNAVAILABLE")
    void failsWithServiceUnavailableOnInvalidApiKey() {
        // 이게 진짜 "경로 없음"으로 잘못 분류되면 사용자에게 "그런 경로는 없다"고
        // 거짓으로 알리게 된다 — 실제로는 우리 쪽 설정(appKey/상품 구독) 문제다.
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(INVALID_API_KEY_RESPONSE));

        assertThatThrownBy(() -> client.findRoute(127.1, 37.5, 127.2, 37.6))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("경로 탐색 자체와 무관한 tmap 에러(예: 필수 파라미터 누락 9401)는 SERVICE_UNAVAILABLE")
    void failsWithServiceUnavailableOnUnmappedTmapCode() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(MISSING_PARAMETER_RESPONSE));

        assertThatThrownBy(() -> client.findRoute(127.1, 37.5, 127.2, 37.6))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("4xx인데 본문이 아예 없어도 예외를 삼키지 않고 SERVICE_UNAVAILABLE로 정리한다")
    void failsWithServiceUnavailableOnEmptyErrorBody() {
        mockServer.expect(requestTo(containsString("/tmap/routes/pedestrian")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.findRoute(127.1, 37.5, 127.2, 37.6))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("인증키가 없으면 호출하지 않고 즉시 실패한다")
    void failsFastWhenApiKeyMissing() {
        TmapPedestrianClient unconfigured = new TmapPedestrianClient(builder, properties(""));

        assertThatThrownBy(() -> unconfigured.findRoute(127.1, 37.5, 127.2, 37.6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TMAP_APP_KEY");

        mockServer.verify(); // 기대한 요청이 없다 = 네트워크를 타지 않았다
    }
}
