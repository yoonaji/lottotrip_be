package com.lottotrip.route.odsay;

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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ODsay 클라이언트 검증. {@code TourApiClientTest}와 같은 구조다.
 *
 * {@link MockRestServiceServer}가 `RestClient` 내부의 "요청을 실제로 보내는 부품"을
 * 가짜로 바꿔치기하므로 네트워크를 아예 타지 않는다.
 */
class OdsayClientTest {

    private static final String BASE_URL = "https://api.odsay.com/v1/api";

    /** 실제 발급받은 키에 `/`가 섞여 있어, 인코딩 함정을 재현하려고 일부러 비슷한 값을 쓴다. */
    private static final String API_KEY = "ab+cd/ef==";
    private static final String ENCODED_API_KEY = "ab%2Bcd%2Fef%3D%3D";

    private static final String SUCCESS_RESPONSE = """
            {
              "result": {
                "path": [
                  {
                    "pathType": 2,
                    "info": {
                      "totalTime": 45,
                      "payment": 1500,
                      "totalWalk": 300,
                      "totalDistance": 12000.0,
                      "busTransitCount": 1,
                      "subwayTransitCount": 0
                    },
                    "subPath": [
                      { "trafficType": 3, "distance": 200, "sectionTime": 5,
                        "startName": "출발지", "endName": "정류장" },
                      { "trafficType": 2, "distance": 8000, "sectionTime": 21, "stationCount": 13,
                        "startName": "정류장", "endName": "강변역(B)",
                        "lane": [ { "busNo": "92" }, { "busNo": "9" } ] },
                      { "trafficType": 3, "distance": 100, "sectionTime": 2,
                        "startName": "강변역(B)", "endName": "도착지" }
                    ]
                  }
                ]
              }
            }
            """;

    /**
     * 2026-08-27에 화이트리스트 등록된 실서버(3.37.104.92)에서 실제로 받아온 응답이다
     * (강릉시외.고속터미널 → 경포현대아파트, 5.8km, 실제 버스 202번 노선).
     * {@code passStopList}(정류장 목록)는 우리 매핑에 쓰지 않아 지워냈다.
     *
     * 이 응답이 가정과 다르게 알려준 것 하나: {@code info.totalDistance}는 이름과 달리
     * 정수가 아니라 `11069.0`처럼 소수로 온다. {@code int}로 선언했다면 이 응답 하나로 파싱이 깨졌을 것이다.
     * 버스 번호도 `"202(동진)"`처럼 방면 표기가 괄호로 붙어 온다 — 순수 숫자만 오는 게 아니다.
     */
    private static final String REAL_CAPTURED_RESPONSE = """
            {
              "result": {
                "searchType": 0,
                "outTrafficCheck": 0,
                "busCount": 4,
                "subwayCount": 0,
                "path": [
                  {
                    "pathType": 2,
                    "info": {
                      "trafficDistance": 9914.0,
                      "totalWalk": 1155,
                      "totalTime": 52,
                      "payment": 1530,
                      "busTransitCount": 1,
                      "subwayTransitCount": 0,
                      "firstStartStation": "강릉시외.고속터미널",
                      "lastEndStation": "경포현대아파트",
                      "totalStationCount": 20,
                      "totalDistance": 11069.0,
                      "totalWalkTime": -1,
                      "checkIntervalTimeOverYn": "N"
                    },
                    "subPath": [
                      { "trafficType": 3, "distance": 452, "sectionTime": 7 },
                      {
                        "trafficType": 2, "distance": 9914, "sectionTime": 34, "stationCount": 20,
                        "lane": [ { "busNo": "202(동진)", "type": 1, "busID": 2350028 } ],
                        "intervalTime": 60,
                        "startName": "강릉시외.고속터미널", "startX": 128.879264, "startY": 37.755103,
                        "endName": "경포현대아파트", "endX": 128.903294, "endY": 37.801189
                      },
                      { "trafficType": 3, "distance": 703, "sectionTime": 11 }
                    ]
                  }
                ]
              }
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private OdsayClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new OdsayClient(builder, properties(API_KEY));
    }

    private OdsayProperties properties(String apiKey) {
        return new OdsayProperties(BASE_URL, apiKey);
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("추천 경로(첫 경로)의 요약과 구간을 그대로 옮긴다")
    void mapsRecommendedRoute() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        OdsayResponse.Path path = client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4);

        assertThat(path.info().totalTime()).isEqualTo(45);
        assertThat(path.info().payment()).isEqualTo(1500);
        assertThat(path.subPath()).hasSize(3);
        mockServer.verify();
    }

    @Test
    @DisplayName("버스 구간은 lane에 여러 노선이 담길 수 있다")
    void mapsMultipleBusLanes() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        OdsayResponse.SubPath busLeg = client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4)
                .subPath().get(1);

        assertThat(busLeg.trafficType()).isEqualTo(2);
        assertThat(busLeg.stationCount()).isEqualTo(13);
        assertThat(busLeg.lane()).extracting(OdsayResponse.Lane::busNo).containsExactly("92", "9");
    }

    @Test
    @DisplayName("도보 구간은 stationCount와 lane이 없어도 통과한다")
    void toleratesMissingWalkFields() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        OdsayResponse.SubPath walkLeg = client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4)
                .subPath().get(0);

        assertThat(walkLeg.trafficType()).isEqualTo(3);
        assertThat(walkLeg.stationCount()).isNull();
        assertThat(walkLeg.lane()).isNull();
    }

    @Test
    @DisplayName("실제 서버 응답(실측)을 오류 없이 파싱한다 — totalDistance는 소수로 온다")
    void parsesRealCapturedResponse() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(REAL_CAPTURED_RESPONSE, MediaType.APPLICATION_JSON));

        OdsayResponse.Path path = client.findRecommendedRoute(128.8761, 37.7519, 128.8954, 37.8021);

        assertThat(path.info().totalTime()).isEqualTo(52);
        assertThat(path.info().payment()).isEqualTo(1530);
        assertThat(path.info().totalDistance()).isEqualTo(11069.0);
        assertThat(path.subPath()).hasSize(3);
        // 실제 버스 번호는 방면 표기가 괄호로 붙어 온다 — 순수 숫자만 오는 게 아니다.
        assertThat(path.subPath().get(1).lane().get(0).busNo()).isEqualTo("202(동진)");
    }

    // ---------- 요청 형태 ----------

    @Test
    @DisplayName("출발 경도·위도를 SX·SY에, 도착 경도·위도를 EX·EY에 싣는다")
    void sendsCoordinatesInOdsayOrder() {
        // 경도/위도가 뒤바뀌면 오류 없이 엉뚱한 결과(또는 0건)가 조용히 온다 — TourAPI의 mapX/mapY와 같은 함정.
        mockServer.expect(requestTo(containsString("SX=126.9")))
                .andExpect(requestTo(containsString("SY=37.5")))
                .andExpect(requestTo(containsString("EX=127.1")))
                .andExpect(requestTo(containsString("EY=37.4")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4);

        mockServer.verify();
    }

    @Test
    @DisplayName("인증키의 특수문자는 정확히 한 번만 인코딩한다")
    void encodesApiKeyExactlyOnce() {
        // 이중 인코딩(%2B → %252B)되면 서버가 키를 못 알아본다.
        mockServer.expect(requestTo(containsString("apiKey=" + ENCODED_API_KEY)))
                .andExpect(requestTo(not(containsString("%25"))))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4);

        mockServer.verify();
    }

    @Test
    @DisplayName("추천순(OPT=0)·한국어(lang=0)·json 출력을 함께 요청한다")
    void sendsCommonParameters() {
        mockServer.expect(requestTo(containsString("OPT=0")))
                .andExpect(requestTo(containsString("lang=0")))
                .andExpect(requestTo(containsString("output=json")))
                .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4);

        mockServer.verify();
    }

    // ---------- 에러 ----------

    @Test
    @DisplayName("검색결과 없음(-99)이면 ROUTE_NOT_FOUND")
    void failsWithRouteNotFoundOnNoResult() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess("""
                        { "error": { "code": "-99", "message": "검색결과가 없습니다" } }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("출발·도착이 700m 이내(-98)면 ROUTE_NOT_FOUND")
    void failsWithRouteNotFoundWhenTooClose() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess("""
                        { "error": { "code": "-98", "message": "출, 도착지가 700m이내입니다" } }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("필수값 오류(-9) 같은 인증·형식 오류는 SERVICE_UNAVAILABLE")
    void failsWithServiceUnavailableOnAuthOrFormatError() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess("""
                        { "error": { "code": "-9", "message": "필수 입력값 누락" } }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("result와 error가 둘 다 없으면 ROUTE_NOT_FOUND — 경로 후보가 0건인 것과 같다")
    void failsWithRouteNotFoundOnEmptyResult() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("서버가 5xx를 주면 SERVICE_UNAVAILABLE")
    void failsOnServerError() {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.findRecommendedRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("인증키가 없으면 호출하지 않고 즉시 실패한다")
    void failsFastWhenApiKeyMissing() {
        OdsayClient unconfigured = new OdsayClient(builder, properties(""));

        assertThatThrownBy(() -> unconfigured.findRecommendedRoute(126.9, 37.5, 127.1, 37.4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ODSAY_API_KEY");

        mockServer.verify(); // 기대한 요청이 없다 = 네트워크를 타지 않았다
    }

    @Test
    @DisplayName("로그에 남기는 URL에서는 인증키를 가린다")
    void masksApiKeyInLogs() {
        String masked = OdsayClient.maskApiKey(
                BASE_URL + "/searchPubTransPathT?apiKey=" + ENCODED_API_KEY + "&SX=126.9");

        assertThat(masked).doesNotContain(ENCODED_API_KEY);
        assertThat(masked).contains("SX=126.9");
    }
}
