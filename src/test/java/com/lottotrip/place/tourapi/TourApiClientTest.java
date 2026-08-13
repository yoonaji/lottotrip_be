package com.lottotrip.place.tourapi;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TourAPI 클라이언트 검증. (roadmap 5-1)
 *
 * <p>실제 공공데이터포털을 부르지 않는다. {@link MockRestServiceServer}가 {@code RestClient} 내부의
 * "요청을 실제로 보내는 부품"을 가짜로 바꿔치기하므로 네트워크를 아예 타지 않는다.
 * 4-4-1 카카오 검증기 테스트와 같은 방식이다.
 *
 * <p>⚠️ <b>여기 적힌 응답 JSON은 TourAPI 문서 기준으로 작성한 가정이다.</b> 실제 응답 모양이 다르면
 * 이 테스트는 전부 통과해도 5-2 적재에서 빈 값·뒤집힌 좌표가 쌓인다. 서비스 키가 준비되면
 * 실제 응답을 1회 받아 필드명을 대조하는 절차가 따로 필요하다. (roadmap 미확정 항목)
 */
class TourApiClientTest {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

    /**
     * 포털이 주는 "디코딩" 키에는 {@code +} {@code /} {@code =} 가 섞여 있다.
     * 이 문자들이 URL에서 어떻게 처리되는지가 이 클라이언트의 핵심 함정이라 일부러 넣었다.
     */
    private static final String SERVICE_KEY = "ab+cd/ef==";
    private static final String ENCODED_SERVICE_KEY = "ab%2Bcd%2Fef%3D%3D";

    /** 관광지 목록 정상 응답 1건. */
    private static final String AREA_BASED_LIST = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": {
                  "items": {
                    "item": [
                      {
                        "contentid": "126508",
                        "contenttypeid": "12",
                        "title": "경복궁",
                        "addr1": "서울특별시 종로구 사직로 161",
                        "addr2": "(세종로)",
                        "areacode": "1",
                        "sigungucode": "23",
                        "cat1": "A02", "cat2": "A0201", "cat3": "A02010100",
                        "firstimage": "http://tong.visitkorea.or.kr/big.jpg",
                        "firstimage2": "http://tong.visitkorea.or.kr/small.jpg",
                        "mapx": "126.9769930325",
                        "mapy": "37.5760836609"
                      }
                    ]
                  },
                  "numOfRows": 100,
                  "pageNo": 1,
                  "totalCount": 250
                }
              }
            }
            """;

    /**
     * 좌표 기반 목록 정상 응답 1건. (5-2)
     *
     * <p><b>이것은 가정이 아니라 2026-08-08에 실제 API에서 받아온 응답이다.</b>
     * 강릉 숙소 좌표(37.7519, 128.8761) 반경 20km로 호출한 결과의 첫 항목이며, 필드 구성을 그대로 옮겼다.
     *
     * <p>여기서 두 가지가 드러난다.
     * <ul>
     *   <li>{@code dist} — 요청 좌표로부터의 거리(미터)를 API가 계산해 준다.</li>
     *   <li>{@code contenttypeid: "32"}(숙박) — 필터 없이 뽑으면 모텔이 여행지로 나온다는 증거다.</li>
     * </ul>
     */
    private static final String LOCATION_BASED_LIST = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": {
                  "items": {
                    "item": [
                      {
                        "contentid": "3535323",
                        "contenttypeid": "32",
                        "title": "에쿠스모텔",
                        "addr1": "강원특별자치도 강릉시 홍제로85번길 34 (홍제동)",
                        "addr2": "",
                        "areacode": "32",
                        "sigungucode": "1",
                        "cat1": "B02", "cat2": "B0201", "cat3": "B02010900",
                        "dist": "442.3427319744332",
                        "firstimage": "",
                        "firstimage2": "",
                        "mapx": "128.8807486691",
                        "mapy": "37.7533209621"
                      }
                    ]
                  },
                  "numOfRows": 100,
                  "pageNo": 1,
                  "totalCount": 768
                }
              }
            }
            """;

    /**
     * 결과가 0건일 때의 응답. <b>{@code items}가 객체가 아니라 빈 문자열로 온다.</b>
     * 공공데이터포털 계열 API의 알려진 특성이라 반드시 견뎌야 한다.
     *
     * <p>✅ 2026-08-08 실물 호출로 확인됨 — 5-1 작성 시점의 가정이 실제와 일치했다.
     */
    private static final String EMPTY_ITEMS = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": { "items": "", "numOfRows": 100, "pageNo": 1, "totalCount": 0 }
              }
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private TourApiClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TourApiClient(builder, properties(SERVICE_KEY));
    }

    private TourApiProperties properties(String serviceKey) {
        return new TourApiProperties(BASE_URL, serviceKey, "ETC", "lottotrip", 100);
    }

    // ---------- 지역 기반 목록: 매핑 ----------

    @Test
    @DisplayName("지역 기반 목록을 항목 리스트로 변환한다")
    void mapsAreaBasedList() {
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(AREA_BASED_LIST, MediaType.APPLICATION_JSON));

        TourApiPage<TourApiPlaceItem> page = client.fetchAreaBasedList(1, 1);

        assertThat(page.items()).hasSize(1);
        TourApiPlaceItem item = page.items().get(0);
        assertThat(item.contentId()).isEqualTo("126508");
        assertThat(item.contentTypeId()).isEqualTo("12");
        assertThat(item.title()).isEqualTo("경복궁");
        assertThat(item.areaCode()).isEqualTo("1");
        assertThat(item.sigunguCode()).isEqualTo("23");
        assertThat(item.firstImage()).isEqualTo("http://tong.visitkorea.or.kr/big.jpg");
        mockServer.verify();
    }

    @Test
    @DisplayName("mapx는 경도, mapy는 위도로 뒤집어 매핑한다")
    void mapsCoordinatesInSwappedOrder() {
        // TourAPI의 mapx/mapy는 이름과 달리 x=경도, y=위도다.
        // 그대로 latitude=mapx로 넣으면 대한민국 장소가 전부 남극 근처로 간다.
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(AREA_BASED_LIST, MediaType.APPLICATION_JSON));

        TourApiPlaceItem item = client.fetchAreaBasedList(1, 1).items().get(0);

        assertThat(item.latitude()).isEqualTo(37.5760836609);   // mapy
        assertThat(item.longitude()).isEqualTo(126.9769930325); // mapx
    }

    @Test
    @DisplayName("좌표가 비어 있으면 위경도는 null이다")
    void returnsNullCoordinateWhenBlank() {
        // 5-2에서 좌표 없는 장소를 걸러낼 수 있어야 한다. places.latitude는 NOT NULL이다.
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(itemWithCoordinate("", ""), MediaType.APPLICATION_JSON));

        TourApiPlaceItem item = client.fetchAreaBasedList(1, 1).items().get(0);

        assertThat(item.latitude()).isNull();
        assertThat(item.longitude()).isNull();
    }

    @Test
    @DisplayName("좌표가 숫자가 아니어도 예외 대신 null이다")
    void returnsNullCoordinateWhenNotNumeric() {
        // 한 건이 깨졌다고 배치 전체가 멈추면 안 된다.
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(itemWithCoordinate("없음", "없음"), MediaType.APPLICATION_JSON));

        TourApiPlaceItem item = client.fetchAreaBasedList(1, 1).items().get(0);

        assertThat(item.latitude()).isNull();
        assertThat(item.longitude()).isNull();
    }

    // ---------- 지역 기반 목록: 요청 형태 ----------

    @Test
    @DisplayName("공통 파라미터와 페이징 정보를 쿼리에 싣는다")
    void sendsCommonParameters() {
        mockServer.expect(requestTo(containsString("MobileOS=ETC")))
                .andExpect(requestTo(containsString("MobileApp=lottotrip")))
                .andExpect(requestTo(containsString("_type=json")))
                .andExpect(requestTo(containsString("numOfRows=100")))
                .andExpect(requestTo(containsString("pageNo=2")))
                .andExpect(requestTo(containsString("areaCode=6")))
                .andRespond(withSuccess(AREA_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchAreaBasedList(6, 2);

        mockServer.verify();
    }

    @Test
    @DisplayName("정렬 기준을 함께 보낸다 — 페이지를 넘겨도 순서가 흔들리지 않게")
    void sendsArrangeParameter() {
        // 정렬을 지정하지 않으면 페이지마다 순서가 달라져 같은 장소를 두 번 받거나 아예 놓칠 수 있다.
        mockServer.expect(requestTo(containsString("arrange=")))
                .andRespond(withSuccess(AREA_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchAreaBasedList(1, 1);

        mockServer.verify();
    }

    @Test
    @DisplayName("서비스 키의 특수문자는 정확히 한 번만 인코딩한다")
    void encodesServiceKeyExactlyOnce() {
        // 이중 인코딩(%2B → %252B)되면 서버가 키를 못 알아본다. 공공데이터포털 연동의 대표적 실패 원인이다.
        mockServer.expect(requestTo(containsString("serviceKey=" + ENCODED_SERVICE_KEY)))
                .andExpect(requestTo(not(containsString("%25"))))
                .andRespond(withSuccess(AREA_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchAreaBasedList(1, 1);

        mockServer.verify();
    }

    // ---------- 페이징 ----------

    @Test
    @DisplayName("총 건수를 보고 다음 페이지가 있는지 알려준다")
    void reportsNextPageExists() {
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(AREA_BASED_LIST, MediaType.APPLICATION_JSON));

        TourApiPage<TourApiPlaceItem> page = client.fetchAreaBasedList(1, 1);

        // 100개씩 1페이지를 받았고 전체가 250건이므로 아직 남았다.
        assertThat(page.totalCount()).isEqualTo(250);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지면 다음이 없다고 알려준다")
    void reportsNoNextPageOnLastPage() {
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(AREA_BASED_LIST.replace("\"pageNo\": 1", "\"pageNo\": 3"),
                        MediaType.APPLICATION_JSON));

        TourApiPage<TourApiPlaceItem> page = client.fetchAreaBasedList(1, 3);

        // 3페이지 × 100건 = 300 ≥ 250 이므로 더 없다.
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("결과가 0건이면 items가 빈 문자열로 와도 빈 리스트다")
    void toleratesEmptyStringItems() {
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(EMPTY_ITEMS, MediaType.APPLICATION_JSON));

        TourApiPage<TourApiPlaceItem> page = client.fetchAreaBasedList(1, 1);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    // ---------- 에러 ----------

    @Test
    @DisplayName("resultCode가 0000이 아니면 SERVICE_UNAVAILABLE")
    void failsOnErrorResultCode() {
        // HTTP는 200인데 본문 헤더에 실패 코드가 담겨 오는 것이 이 API의 방식이다.
        // 상태 코드만 보고 성공으로 넘기면 빈 데이터를 정상으로 착각한다.
        String limitExceeded = """
                {
                  "response": {
                    "header": {
                      "resultCode": "22",
                      "resultMsg": "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"
                    }
                  }
                }
                """;
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(limitExceeded, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchAreaBasedList(1, 1))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("인증 실패 XML이 오면 SERVICE_UNAVAILABLE")
    void failsOnXmlErrorPage() {
        // _type=json을 보내도 키 자체가 거부되면 XML 에러 문서가 온다. 파싱 실패로 터지게 두지 않는다.
        String xml = """
                <OpenAPI_ServiceResponse>
                  <cmmMsgHeader>
                    <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                    <returnReasonCode>30</returnReasonCode>
                  </cmmMsgHeader>
                </OpenAPI_ServiceResponse>
                """;
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.fetchAreaBasedList(1, 1))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("서버가 5xx를 주면 SERVICE_UNAVAILABLE")
    void failsOnServerError() {
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchAreaBasedList(1, 1))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("서버가 4xx를 줘도 SERVICE_UNAVAILABLE — 배치라 사용자에게 돌려줄 400이 없다")
    void failsOnClientError() {
        mockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.fetchAreaBasedList(1, 1))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("서비스 키가 없으면 호출하지 않고 즉시 실패한다")
    void failsFastWhenServiceKeyMissing() {
        // 키 없이 부르면 어차피 인증 오류다. 설정 실수는 네트워크를 타기 전에 드러나는 편이 낫다.
        TourApiClient unconfigured = new TourApiClient(builder, properties(""));

        assertThatThrownBy(() -> unconfigured.fetchAreaBasedList(1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOUR_API_SERVICE_KEY");

        mockServer.verify(); // 기대한 요청이 없다 = 네트워크를 타지 않았다
    }

    @Test
    @DisplayName("로그에 남기는 URL에서는 서비스 키를 가린다")
    void masksServiceKeyInLogs() {
        // 실패 로그에 URL을 통째로 찍으면 인증키가 로그 파일에 그대로 남는다.
        String masked = TourApiClient.maskServiceKey(
                BASE_URL + "/areaBasedList2?serviceKey=" + ENCODED_SERVICE_KEY + "&pageNo=1");

        assertThat(masked).doesNotContain(ENCODED_SERVICE_KEY);
        assertThat(masked).contains("pageNo=1");
    }

    // ---------- 상세 정보 ----------

    @Test
    @DisplayName("상세 조회는 개요를 돌려준다")
    void fetchesDetailCommon() {
        String detail = """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": { "item": [ {
                        "contentid": "126508",
                        "title": "경복궁",
                        "overview": "조선 왕조의 법궁이다.",
                        "homepage": "https://royal.cha.go.kr"
                      } ] },
                      "numOfRows": 1, "pageNo": 1, "totalCount": 1
                    }
                  }
                }
                """;
        mockServer.expect(requestTo(containsString("/detailCommon2")))
                .andExpect(requestTo(containsString("contentId=126508")))
                .andRespond(withSuccess(detail, MediaType.APPLICATION_JSON));

        Optional<TourApiDetailItem> found = client.fetchDetailCommon("126508");

        assertThat(found).isPresent();
        assertThat(found.get().overview()).isEqualTo("조선 왕조의 법궁이다.");
        mockServer.verify();
    }

    @Test
    @DisplayName("상세 정보가 없으면 빈 Optional이다")
    void returnsEmptyWhenNoDetail() {
        mockServer.expect(requestTo(containsString("/detailCommon2")))
                .andRespond(withSuccess(EMPTY_ITEMS, MediaType.APPLICATION_JSON));

        assertThat(client.fetchDetailCommon("999")).isEmpty();
    }

    // ---------- 이미지 ----------

    @Test
    @DisplayName("이미지 목록을 돌려준다")
    void fetchesDetailImages() {
        String images = """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": { "item": [
                        { "contentid": "126508", "originimgurl": "http://tong.visitkorea.or.kr/1.jpg",
                          "smallimageurl": "http://tong.visitkorea.or.kr/1s.jpg", "imgname": "경복궁 전경" },
                        { "contentid": "126508", "originimgurl": "http://tong.visitkorea.or.kr/2.jpg",
                          "smallimageurl": "http://tong.visitkorea.or.kr/2s.jpg", "imgname": "근정전" }
                      ] },
                      "numOfRows": 10, "pageNo": 1, "totalCount": 2
                    }
                  }
                }
                """;
        mockServer.expect(requestTo(containsString("/detailImage2")))
                .andExpect(requestTo(containsString("contentId=126508")))
                .andRespond(withSuccess(images, MediaType.APPLICATION_JSON));

        List<TourApiImageItem> found = client.fetchDetailImages("126508");

        assertThat(found).hasSize(2);
        assertThat(found.get(0).originImgUrl()).isEqualTo("http://tong.visitkorea.or.kr/1.jpg");
        assertThat(found.get(1).imgName()).isEqualTo("근정전");
        mockServer.verify();
    }

    @Test
    @DisplayName("이미지가 없으면 빈 리스트다")
    void returnsEmptyImageList() {
        mockServer.expect(requestTo(containsString("/detailImage2")))
                .andRespond(withSuccess(EMPTY_ITEMS, MediaType.APPLICATION_JSON));

        assertThat(client.fetchDetailImages("126508")).isEmpty();
    }

    // ---------- 지역 코드 ----------

    @Test
    @DisplayName("지역 코드를 지정하지 않으면 시도 목록을 받는다")
    void fetchesStates() {
        mockServer.expect(requestTo(containsString("/areaCode2")))
                .andExpect(requestTo(not(containsString("areaCode="))))
                .andRespond(withSuccess(areaCodeResponse("1", "서울"), MediaType.APPLICATION_JSON));

        List<TourApiAreaCodeItem> found = client.fetchAreaCodes(null);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).code()).isEqualTo("1");
        assertThat(found.get(0).name()).isEqualTo("서울");
        mockServer.verify();
    }

    @Test
    @DisplayName("지역 코드를 지정하면 그 시도의 시군구 목록을 받는다")
    void fetchesCities() {
        mockServer.expect(requestTo(containsString("/areaCode2")))
                .andExpect(requestTo(containsString("areaCode=1")))
                .andRespond(withSuccess(areaCodeResponse("23", "종로구"), MediaType.APPLICATION_JSON));

        List<TourApiAreaCodeItem> found = client.fetchAreaCodes(1);

        assertThat(found.get(0).name()).isEqualTo("종로구");
        mockServer.verify();
    }

    // ---------- 좌표 기반 목록 (5-2) ----------

    @Test
    @DisplayName("좌표 기반 목록을 항목 리스트로 변환한다")
    void mapsLocationBasedList() {
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(LOCATION_BASED_LIST, MediaType.APPLICATION_JSON));

        TourApiPage<TourApiPlaceItem> page =
                client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1);

        assertThat(page.items()).hasSize(1);
        TourApiPlaceItem item = page.items().get(0);
        assertThat(item.contentId()).isEqualTo("3535323");
        assertThat(item.title()).isEqualTo("에쿠스모텔");
        assertThat(item.areaCode()).isEqualTo("32");
        assertThat(item.sigunguCode()).isEqualTo("1");
        mockServer.verify();
    }

    @Test
    @DisplayName("응답의 dist를 거리(미터)로 읽는다 — 우리가 거리를 계산하지 않는다")
    void readsDistanceFromResponse() {
        // locationBasedList2는 요청 좌표로부터의 거리를 dist(미터)로 준다.
        // 직접 Haversine을 구현하면 API가 이미 준 값과 미세하게 어긋나 정렬 순서가 뒤집힐 수 있다.
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(LOCATION_BASED_LIST, MediaType.APPLICATION_JSON));

        TourApiPlaceItem item = client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1)
                .items().get(0);

        assertThat(item.distanceMeters()).isEqualTo(442.3427319744332);
    }

    @Test
    @DisplayName("dist가 숫자가 아니면 예외 대신 null이다")
    void returnsNullDistanceWhenNotNumeric() {
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(LOCATION_BASED_LIST.replace("442.3427319744332", ""),
                        MediaType.APPLICATION_JSON));

        TourApiPlaceItem item = client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1)
                .items().get(0);

        assertThat(item.distanceMeters()).isNull();
    }

    @Test
    @DisplayName("경도를 mapX에, 위도를 mapY에 싣는다")
    void sendsLongitudeAsMapXAndLatitudeAsMapY() {
        // ⚠️ 이 테스트가 5-2에서 가장 중요하다. 뒤집어 보내면 API는 오류 대신 정상 응답
        // (0건 또는 엉뚱한 장소)을 주기 때문에, 틀려도 드러나지 않고 결과만 조용히 이상해진다.
        mockServer.expect(requestTo(containsString("mapX=128.8761")))
                .andExpect(requestTo(containsString("mapY=37.7519")))
                .andRespond(withSuccess(LOCATION_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1);

        mockServer.verify();
    }

    @Test
    @DisplayName("반경은 미터 단위로 싣는다")
    void sendsRadiusInMeters() {
        // 우리 도메인은 km로 말하지만(walk 1km / car 20km) API는 미터를 받는다.
        // 20을 그대로 보내면 반경 20m가 되어 후보가 0건이 된다.
        mockServer.expect(requestTo(containsString("radius=20000")))
                .andRespond(withSuccess(LOCATION_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1);

        mockServer.verify();
    }

    @Test
    @DisplayName("거리순 정렬(arrange=E)로 요청한다")
    void sendsDistanceArrange() {
        // 가까운 곳부터 받아야 페이지를 끝까지 넘기지 않고도 쓸 만한 후보를 확보할 수 있다.
        mockServer.expect(requestTo(containsString("arrange=E")))
                .andRespond(withSuccess(LOCATION_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1);

        mockServer.verify();
    }

    @Test
    @DisplayName("contentTypeId를 주면 종류를 좁혀 요청한다")
    void sendsContentTypeIdWhenGiven() {
        // 필터가 없으면 숙박·음식점이 여행지로 뽑힌다. (실측: 100건 중 음식점 71 · 숙박 17)
        mockServer.expect(requestTo(containsString("contentTypeId=12")))
                .andRespond(withSuccess(LOCATION_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchLocationBasedList(37.7519, 128.8761, 20000, "12", 1);

        mockServer.verify();
    }

    @Test
    @DisplayName("contentTypeId가 없으면 파라미터를 아예 빼고 요청한다")
    void omitsContentTypeIdWhenNull() {
        // 빈 값으로 보내면 API가 "종류 없음"으로 해석해 0건을 줄 수 있다. 아예 빼는 편이 안전하다.
        mockServer.expect(requestTo(not(containsString("contentTypeId"))))
                .andRespond(withSuccess(LOCATION_BASED_LIST, MediaType.APPLICATION_JSON));

        client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1);

        mockServer.verify();
    }

    @Test
    @DisplayName("반경이 API 상한(20km)을 넘으면 호출하지 않고 거절한다")
    void rejectsRadiusAboveApiLimit() {
        // TourAPI의 radius 상한이 20000m다. 넘겨도 오류 없이 조용히 잘린 결과가 오므로
        // "반경 50km로 뽑았다"고 착각하게 된다. 우리가 먼저 막는다.
        assertThatThrownBy(() -> client.fetchLocationBasedList(37.7519, 128.8761, 20001, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20000");

        mockServer.verify(); // 요청이 나가지 않았다
    }

    @Test
    @DisplayName("반경이 0 이하면 거절한다")
    void rejectsNonPositiveRadius() {
        assertThatThrownBy(() -> client.fetchLocationBasedList(37.7519, 128.8761, 0, null, 1))
                .isInstanceOf(IllegalArgumentException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("반경 안에 아무것도 없으면 빈 리스트다 — 0건을 어떻게 다룰지는 부르는 쪽 몫")
    void returnsEmptyWhenNothingInRadius() {
        // 실측으로 확인한 응답이다. 0건이면 items가 객체가 아니라 빈 문자열로 온다.
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(EMPTY_ITEMS, MediaType.APPLICATION_JSON));

        TourApiPage<TourApiPlaceItem> page =
                client.fetchLocationBasedList(36.0, 130.5, 1000, null, 1);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("좌표 기반 목록도 실패 코드는 SERVICE_UNAVAILABLE로 모은다")
    void failsOnErrorResultCodeForLocationBasedList() {
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": { "resultCode": "22", "resultMsg": "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR" }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchLocationBasedList(37.7519, 128.8761, 20000, null, 1))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    // ---------- 헬퍼 ----------

    private String itemWithCoordinate(String mapx, String mapy) {
        return AREA_BASED_LIST
                .replace("\"mapx\": \"126.9769930325\"", "\"mapx\": \"" + mapx + "\"")
                .replace("\"mapy\": \"37.5760836609\"", "\"mapy\": \"" + mapy + "\"");
    }

    private String areaCodeResponse(String code, String name) {
        return """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": { "item": [ { "rnum": 1, "code": "%s", "name": "%s" } ] },
                      "numOfRows": 100, "pageNo": 1, "totalCount": 1
                    }
                  }
                }
                """.formatted(code, name);
    }
}
