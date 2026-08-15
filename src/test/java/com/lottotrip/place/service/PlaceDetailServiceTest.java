package com.lottotrip.place.service;

import com.lottotrip.place.dto.PlaceDetail;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 장소 세부조회 검증. (roadmap 5-10, 결정 10)
 *
 * <p><b>이 조회가 공공데이터 API를 사용자 요청에 반응해 부르는 유일한 지점이다.</b>
 * 추첨은 DB로만 하기로 했으므로(결정 10), 공모전 규정(결정 7 — 오픈API 실시간 호출 필수)을
 * 만족시키는 것도 여기다.
 *
 * <p>가장 중요한 성질은 <b>바깥이 실패해도 우리 정보는 나가야 한다</b>는 것이다.
 * 공공데이터포털이 멈췄다고 사용자가 뽑은 장소를 못 보면 안 된다.
 */
class PlaceDetailServiceTest {

    private static final String DETAIL = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": {
                  "items": {
                    "item": [
                      {
                        "contentid": "126508",
                        "title": "사천진해변",
                        "overview": "강릉시 사천면에 있는 조용한 해변으로, 일출 명소로 알려져 있다.",
                        "homepage": "<a href=\\"https://www.gn.go.kr\\">강릉시청</a>"
                      }
                    ]
                  },
                  "numOfRows": 1, "pageNo": 1, "totalCount": 1
                }
              }
            }
            """;

    /** 결과 0건. 공공데이터포털 계열은 {@code items}가 객체가 아니라 빈 문자열로 온다. */
    private static final String EMPTY = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": { "items": "", "numOfRows": 0, "pageNo": 1, "totalCount": 0 }
              }
            }
            """;

    private MockRestServiceServer mockServer;
    private PlaceDetailService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder,
                new TourApiProperties("https://apis.data.go.kr/B551011/KorService2", null, "test-key", null, null, 100));
        service = new PlaceDetailService(client);
    }

    private Place savedPlace() {
        return Place.builder()
                .contentId("126508")
                .contentTypeId("12")
                .name("사천진해변")
                .category(TravelCategory.BEACH)
                .address("강원특별자치도 강릉시 사천면")
                .latitude(37.8021)
                .longitude(128.8954)
                .build();
    }

    @Test
    @DisplayName("DB에 담아 둔 정보와 실시간으로 받은 소개글을 합쳐 준다")
    void combinesStoredAndLiveDetail() {
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withSuccess(DETAIL, MediaType.APPLICATION_JSON));

        PlaceDetail detail = service.describe(savedPlace());

        assertThat(detail.name()).isEqualTo("사천진해변");
        assertThat(detail.category()).isEqualTo("해변");
        assertThat(detail.latitude()).isEqualTo(37.8021);
        assertThat(detail.description()).contains("일출 명소");
        assertThat(detail.liveDetailLoaded()).isTrue();
    }

    @Test
    @DisplayName("장소 코드로 조회한다 — 이 값이 없으면 부를 수가 없다")
    void callsWithContentId() {
        mockServer.expect(requestTo(containsString("contentId=126508")))
                .andRespond(withSuccess(DETAIL, MediaType.APPLICATION_JSON));

        service.describe(savedPlace());

        mockServer.verify();
    }

    @Test
    @DisplayName("홈페이지 값의 HTML 태그를 걷어내고 주소만 남긴다")
    void extractsHomepageUrl() {
        // TourAPI는 홈페이지를 <a href="...">이름</a> 형태의 HTML로 준다.
        // 그대로 내려보내면 앱이 링크로 쓸 수 없다.
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withSuccess(DETAIL, MediaType.APPLICATION_JSON));

        PlaceDetail detail = service.describe(savedPlace());

        assertThat(detail.homepageUrl()).isEqualTo("https://www.gn.go.kr");
    }

    // ---------- 바깥이 실패해도 우리 정보는 나간다 ----------

    @Test
    @DisplayName("공공 API가 실패해도 DB에 있는 정보는 그대로 준다")
    void survivesApiFailure() {
        // 공공데이터포털이 멈췄다고 사용자가 뽑은 장소를 못 보면 안 된다.
        // 이 서비스는 정보를 '더해 주는' 역할이지 '없으면 안 되는' 역할이 아니다.
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withServerError());

        PlaceDetail detail = service.describe(savedPlace());

        assertThat(detail.name()).isEqualTo("사천진해변");
        assertThat(detail.address()).isEqualTo("강원특별자치도 강릉시 사천면");
        assertThat(detail.description()).isNull();
        assertThat(detail.liveDetailLoaded()).isFalse();
    }

    @Test
    @DisplayName("인증키 오류처럼 200인데 실패인 응답도 견딘다")
    void survivesBusinessErrorInsideOkResponse() {
        // TourAPI는 인증키 오류·할당량 초과를 HTTP 200 본문의 resultCode로만 알려 준다.
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": { "resultCode": "22", "resultMsg": "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR" }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PlaceDetail detail = service.describe(savedPlace());

        assertThat(detail.name()).isEqualTo("사천진해변");
        assertThat(detail.liveDetailLoaded()).isFalse();
    }

    @Test
    @DisplayName("상세가 0건이어도 DB 정보는 그대로 준다")
    void survivesEmptyDetail() {
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        PlaceDetail detail = service.describe(savedPlace());

        assertThat(detail.name()).isEqualTo("사천진해변");
        assertThat(detail.description()).isNull();
        assertThat(detail.liveDetailLoaded()).isFalse();
    }

    @Test
    @DisplayName("장소 코드가 없으면 부르지 않고 DB 정보만 준다")
    void skipsCallWhenContentIdMissing() {
        // 적재 경로를 거치지 않은 장소는 코드가 없을 수 있다. 그때 빈 코드로 부르면
        // 의미 없는 호출이 나가고 할당량만 깎인다.
        Place noContentId = Place.builder()
                .contentId(null)
                .name("코드없는장소")
                .category(TravelCategory.NATURE)
                .latitude(37.0)
                .longitude(128.0)
                .build();

        PlaceDetail detail = service.describe(noContentId);

        assertThat(detail.name()).isEqualTo("코드없는장소");
        assertThat(detail.liveDetailLoaded()).isFalse();
        mockServer.verify(); // 요청이 나가지 않았다
    }

    @Test
    @DisplayName("타임아웃처럼 연결 자체가 실패해도 견딘다")
    void survivesConnectionFailure() {
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withStatus());

        PlaceDetail detail = service.describe(savedPlace());

        assertThat(detail.liveDetailLoaded()).isFalse();
    }

    private static org.springframework.test.web.client.ResponseCreator withStatus() {
        return request -> {
            throw new java.io.IOException("connection reset");
        };
    }

    @Test
    @DisplayName("서비스 이용 불가 응답도 견딘다")
    void survivesServiceUnavailable() {
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        PlaceDetail detail = service.describe(savedPlace());

        assertThat(detail.liveDetailLoaded()).isFalse();
    }

    private static org.springframework.test.web.client.ResponseCreator withStatus(HttpStatus status) {
        return org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(status);
    }
}
