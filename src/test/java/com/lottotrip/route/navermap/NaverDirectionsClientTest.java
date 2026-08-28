package com.lottotrip.route.navermap;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * NCP Maps Directions 5 클라이언트 검증. {@code OdsayClientTest}와 같은 구조다.
 */
class NaverDirectionsClientTest {

    private static final String BASE_URL = "https://naveropenapi.apigw.ntruss.com/map-direction/v1";
    private static final String API_KEY_ID = "test-key-id";
    private static final String API_KEY = "test-key";

    private static final String SUCCESS_RESPONSE = """
            {
              "code": 0,
              "message": "길찾기를 성공하였습니다.",
              "route": {
                "trafast": [
                  { "summary": { "distance": 11069.0, "duration": 1620000, "tollFare": 0, "taxiFare": 12500 } }
                ]
              }
            }
            """;

    private static final String NO_ROUTE_RESPONSE = """
            { "code": 1, "message": "출발지에서 경로를 찾을 수 없습니다.", "route": {} }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private NaverDirectionsClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new NaverDirectionsClient(builder, properties(API_KEY_ID, API_KEY));
    }

    private NaverDirectionsProperties properties(String apiKeyId, String apiKey) {
        return new NaverDirectionsProperties(BASE_URL, apiKeyId, apiKey);
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("최단시간 경로(trafast)의 요약을 그대로 옮긴다 — duration은 밀리초라 분으로 나누지 않는다")
    void mapsFastestRouteSummary() {
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        NaverDirectionsResponse.TrafastRoute route = client.findFastestRoute(126.9, 37.5, 127.1, 37.4);

        assertThat(route.summary().distance()).isEqualTo(11069.0);
        assertThat(route.summary().duration()).isEqualTo(1_620_000L);
        assertThat(route.summary().tollFare()).isZero();
        assertThat(route.summary().taxiFare()).isEqualTo(12500);
        mockServer.verify();
    }

    // ---------- 요청 형태 ----------

    @Test
    @DisplayName("출발 경도,위도를 start에, 도착 경도,위도를 goal에 싣는다")
    void sendsCoordinatesInNaverOrder() {
        mockServer.expect(requestTo(containsString("start=126.9,37.5")))
                .andExpect(requestTo(containsString("goal=127.1,37.4")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findFastestRoute(126.9, 37.5, 127.1, 37.4);

        mockServer.verify();
    }

    @Test
    @DisplayName("인증키를 URL이 아니라 헤더로 싣는다")
    void sendsApiKeysAsHeaders() {
        mockServer.expect(requestTo(containsString("/driving")))
                .andExpect(header("x-ncp-apigw-api-key-id", API_KEY_ID))
                .andExpect(header("x-ncp-apigw-api-key", API_KEY))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findFastestRoute(126.9, 37.5, 127.1, 37.4);

        mockServer.verify();
    }

    @Test
    @DisplayName("최단시간 옵션(trafast)으로 요청한다")
    void sendsFastestOption() {
        mockServer.expect(requestTo(containsString("option=trafast")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findFastestRoute(126.9, 37.5, 127.1, 37.4);

        mockServer.verify();
    }

    // ---------- 에러 ----------

    @Test
    @DisplayName("code가 0이 아니면 ROUTE_NOT_FOUND — HTTP는 200이어도 경로 탐색 실패다")
    void failsWithRouteNotFoundOnNonZeroCode() {
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withSuccess(NO_ROUTE_RESPONSE, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findFastestRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("route가 있어도 trafast가 비어 있으면 ROUTE_NOT_FOUND")
    void failsWithRouteNotFoundOnEmptyTrafast() {
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withSuccess("""
                        { "code": 0, "message": "OK", "route": { "trafast": [] } }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findFastestRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("서버(게이트웨이)가 5xx를 주면 SERVICE_UNAVAILABLE")
    void failsOnServerError() {
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.findFastestRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("게이트웨이가 인증키 오류로 4xx를 줘도 SERVICE_UNAVAILABLE")
    void failsOnAuthError() {
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.findFastestRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("인증키가 하나라도 없으면 호출하지 않고 즉시 실패한다")
    void failsFastWhenApiKeyMissing() {
        NaverDirectionsClient unconfigured = new NaverDirectionsClient(builder, properties(API_KEY_ID, ""));

        assertThatThrownBy(() -> unconfigured.findFastestRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NAVER_MAP_API_KEY");

        mockServer.verify(); // 기대한 요청이 없다 = 네트워크를 타지 않았다
    }
}
