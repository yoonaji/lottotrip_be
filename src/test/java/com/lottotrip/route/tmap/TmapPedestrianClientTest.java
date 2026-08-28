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

    /** 요약이 첫 feature에만 실린다 — 문서 기준 가정. 실제 응답은 아직 실측 전이다. */
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
    @DisplayName("인증키 오류로 4xx를 줘도 SERVICE_UNAVAILABLE — 문서에 에러코드 체계가 없어 뭉뚱그린다")
    void failsOnClientError() {
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
