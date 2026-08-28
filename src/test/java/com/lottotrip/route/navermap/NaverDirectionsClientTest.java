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

    private static final String BASE_URL = "https://maps.apigw.ntruss.com/map-direction/v1";
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

    /** 공식 문서(2026-08-28 실측)의 에러코드 표 기준. 경로 탐색 실패는 HTTP 200이 아니라 400으로 온다. */
    private static final String NO_ROUTE_RESPONSE = """
            { "code": 3, "message": "자동차 길 찾기 결과를 제공할 수 없습니다.", "route": {} }
            """;

    /**
     * 실제로 받아온 응답이다(2026-08-28, 화이트리스트된 서버에서 직접 호출).
     * NCP가 애플리케이션 키는 맞는데 "이 API 구독이 안 됐다"고 거절할 때의 모양 —
     * 우리 응답 스키마(code/message/route)와 완전히 다르다.
     */
    private static final String SUBSCRIPTION_REQUIRED_RESPONSE = """
            { "error": { "errorCode": "210", "message": "Permission Denied",
                         "details": "A subscription to the API is required." } }
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
    @DisplayName("경로 탐색 실패(HTTP 400 + 공식 에러코드 1~5)면 ROUTE_NOT_FOUND")
    void failsWithRouteNotFoundOnDocumentedErrorCode() {
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(NO_ROUTE_RESPONSE));

        assertThatThrownBy(() -> client.findFastestRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("route가 있어도 trafast가 비어 있으면 ROUTE_NOT_FOUND — 문서상 실제로는 없어야 하는 방어 케이스")
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
    @DisplayName("실제로 받은 구독 필요(errorCode 210) 응답도 SERVICE_UNAVAILABLE이지 ROUTE_NOT_FOUND가 아니다")
    void failsWithServiceUnavailableOnSubscriptionRequired() {
        // 이게 진짜 "경로 없음"으로 잘못 분류되면 사용자에게 "그런 경로는 없다"고
        // 거짓으로 알리게 된다 — 실제로는 우리 쪽 설정(구독) 문제다.
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(SUBSCRIPTION_REQUIRED_RESPONSE));

        assertThatThrownBy(() -> client.findFastestRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("4xx인데 본문이 아예 없어도 예외를 삼키지 않고 SERVICE_UNAVAILABLE로 정리한다")
    void failsWithServiceUnavailableOnEmptyErrorBody() {
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
