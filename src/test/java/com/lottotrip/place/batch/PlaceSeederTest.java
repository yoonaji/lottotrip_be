package com.lottotrip.place.batch;

import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.PlaceMedia;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiProperties;
import com.lottotrip.place.tourapi.TravelCategoryMapper;
import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 강원 장소 적재 검증. (roadmap 5-9, 결정 10)
 *
 * <p>DB는 진짜를 쓴다. "두 번 돌려도 중복되지 않는다"는 실제 UNIQUE 제약이 걸려야 증명되고,
 * "좌표 없는 장소는 건너뛴다"도 NOT NULL이 실재해야 의미가 있다.
 * 바깥 호출만 {@link MockRestServiceServer}로 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlaceSeederTest extends PostgresContainerSupport {

    private static final String GANGWON = "32";

    /**
     * 한 페이지짜리 목록. <b>실물 응답의 필드 구성을 따랐다.</b>
     *
     * <p>일부러 섞어 두었다 — 적재 대상(관광지·문화시설)과 제외 대상(숙박·음식점),
     * 그리고 좌표가 빠진 항목이 함께 들어 있다.
     */
    private static final String PAGE = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": {
                  "items": {
                    "item": [
                      {
                        "contentid": "126508", "contenttypeid": "12",
                        "title": "사천진해변", "addr1": "강원특별자치도 강릉시 사천면", "addr2": "",
                        "areacode": "32", "sigungucode": "1",
                        "cat1": "A01", "cat2": "A0101", "cat3": "A01011200",
                        "firstimage": "https://cdn.example.com/beach.jpg", "firstimage2": "",
                        "mapx": "128.8954", "mapy": "37.8021",
                        "modifiedtime": "20240115103045"
                      },
                      {
                        "contentid": "126509", "contenttypeid": "14",
                        "title": "강릉시립박물관", "addr1": "강원특별자치도 강릉시", "addr2": "",
                        "areacode": "32", "sigungucode": "1",
                        "cat1": "A02", "cat2": "A0206", "cat3": "A02060100",
                        "firstimage": "", "firstimage2": "",
                        "mapx": "128.8761", "mapy": "37.7519",
                        "modifiedtime": "20240220090000"
                      },
                      {
                        "contentid": "3535323", "contenttypeid": "32",
                        "title": "에쿠스모텔", "addr1": "강원특별자치도 강릉시", "addr2": "",
                        "areacode": "32", "sigungucode": "1",
                        "cat1": "B02", "cat2": "B0201", "cat3": "B02010900",
                        "firstimage": "", "firstimage2": "",
                        "mapx": "128.8807", "mapy": "37.7533",
                        "modifiedtime": "20240301120000"
                      },
                      {
                        "contentid": "999001", "contenttypeid": "39",
                        "title": "초당순두부", "addr1": "강원특별자치도 강릉시", "addr2": "",
                        "areacode": "32", "sigungucode": "1",
                        "cat1": "A05", "cat2": "A0502", "cat3": "A05020100",
                        "firstimage": "", "firstimage2": "",
                        "mapx": "128.8900", "mapy": "37.7900",
                        "modifiedtime": "20240301120000"
                      },
                      {
                        "contentid": "999002", "contenttypeid": "12",
                        "title": "좌표없는관광지", "addr1": "강원특별자치도", "addr2": "",
                        "areacode": "32", "sigungucode": "1",
                        "cat1": "A01", "cat2": "A0102", "cat3": "A01020100",
                        "firstimage": "", "firstimage2": "",
                        "mapx": "", "mapy": "",
                        "modifiedtime": "20240301120000"
                      }
                    ]
                  },
                  "numOfRows": 100, "pageNo": 1, "totalCount": 5
                }
              }
            }
            """;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private PlaceMediaRepository placeMediaRepository;

    private MockRestServiceServer mockServer;
    private PlaceSeeder seeder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder,
                new TourApiProperties("https://apis.data.go.kr/B551011/KorService2", "test-key", null, null, 100));
        seeder = new PlaceSeeder(client, placeRepository, placeMediaRepository, stateRepository,
                cityRepository, new TravelCategoryMapper());
    }

    /** 지역코드 시드가 이미 돌아간 상태를 만든다. 장소를 시·군에 이으려면 이게 먼저다. */
    private State seededGangwon() {
        State gangwon = stateRepository.save(State.create("강원", GANGWON));
        cityRepository.save(City.create(gangwon, "강릉시", "1"));
        return gangwon;
    }

    private void expectOnePage() {
        mockServer.expect(requestTo(containsString("areaBasedList2")))
                .andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("여행지 3종만 적재한다 — 숙박·음식점은 건너뛴다")
    void seedsOnlyTravelContentTypes() {
        // 필터 없이 담으면 모텔이 여행지로 뽑힌다.
        seededGangwon();
        expectOnePage();

        seeder.seed(GANGWON);

        assertThat(placeRepository.findAll())
                .extracting(Place::getName)
                .containsExactlyInAnyOrder("사천진해변", "강릉시립박물관");
    }

    @Test
    @DisplayName("좌표가 없는 장소는 건너뛴다 — 저장해도 추첨에 쓸 수 없다")
    void skipsPlaceWithoutCoordinate() {
        // latitude·longitude는 NOT NULL이다. 그대로 넣으면 그 한 건 때문에 배치가 멈춘다.
        seededGangwon();
        expectOnePage();

        seeder.seed(GANGWON);

        assertThat(placeRepository.findAll())
                .extracting(Place::getName)
                .doesNotContain("좌표없는관광지");
    }

    @Test
    @DisplayName("분류코드를 우리 카테고리로 옮겨 담는다")
    void mapsCategory() {
        seededGangwon();
        expectOnePage();

        seeder.seed(GANGWON);

        Place beach = placeRepository.findByContentId("126508").orElseThrow();
        assertThat(beach.getCategory()).isEqualTo(TravelCategory.BEACH);
        assertThat(beach.getContentTypeId()).isEqualTo("12");
    }

    @Test
    @DisplayName("좌표·주소·이미지·수정일시를 함께 담는다")
    void storesPlaceDetails() {
        seededGangwon();
        expectOnePage();

        seeder.seed(GANGWON);

        Place beach = placeRepository.findByContentId("126508").orElseThrow();
        // mapx가 경도, mapy가 위도다. 뒤집히면 장소가 통째로 엉뚱한 곳에 찍힌다.
        assertThat(beach.getLatitude()).isEqualTo(37.8021);
        assertThat(beach.getLongitude()).isEqualTo(128.8954);
        assertThat(beach.getAddress()).isEqualTo("강원특별자치도 강릉시 사천면");
        assertThat(beach.getModifiedTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 45));
    }

    @Test
    @DisplayName("지역코드로 시·군을 이어 붙인다")
    void linksCityByRegionCode() {
        // TourAPI는 지역을 코드로만 준다. 5-8 시드가 만들어 둔 표를 보고 잇는다.
        State gangwon = seededGangwon();
        expectOnePage();

        seeder.seed(GANGWON);

        Place beach = placeRepository.findByContentId("126508").orElseThrow();
        assertThat(beach.getCity()).isNotNull();
        assertThat(beach.getCity().getCityName()).isEqualTo("강릉시");
        assertThat(beach.getCity().getState().getId()).isEqualTo(gangwon.getId());
    }

    @Test
    @DisplayName("시·군을 못 찾아도 장소는 담는다 — 지역 시드가 늦어도 적재는 굴러가야 한다")
    void keepsPlaceWhenCityUnknown() {
        // city_id를 nullable로 둔 이유가 이것이다. 여기서 멈추면 시드 순서에 적재가 묶인다.
        stateRepository.save(State.create("강원", GANGWON)); // 시·군은 시드하지 않았다
        expectOnePage();

        seeder.seed(GANGWON);

        Place beach = placeRepository.findByContentId("126508").orElseThrow();
        assertThat(beach.getCity()).isNull();
    }

    @Test
    @DisplayName("두 번 돌려도 장소가 중복되지 않고 바뀐 정보만 반영된다")
    void isIdempotent() {
        seededGangwon();
        expectOnePage();
        seeder.seed(GANGWON);

        mockServer.reset();
        mockServer.expect(requestTo(containsString("areaBasedList2")))
                .andRespond(withSuccess(PAGE.replace("사천진해변", "사천진해수욕장"),
                        MediaType.APPLICATION_JSON));
        seeder.seed(GANGWON);

        assertThat(placeRepository.findAll()).hasSize(2);
        assertThat(placeRepository.findByContentId("126508").orElseThrow().getName())
                .isEqualTo("사천진해수욕장");
    }

    @Test
    @DisplayName("다음 페이지가 남아 있으면 끝까지 넘긴다")
    void followsPagination() {
        // totalCount가 페이지 크기(100)보다 크면 아직 남은 것이다. 한 페이지만 받고 끝내면
        // 강원 2,373건 중 100건만 담긴다.
        //
        // ⚠️ 응답의 numOfRows로는 판단하지 않는다. 실측(2026-08-14) 결과 이 API는
        //    마지막 페이지에 '실제 담긴 건수'를, 범위 밖 페이지에 0을 담아 주기 때문이다.
        //    우리가 요청한 크기(100)와 totalCount로만 따진다.
        seededGangwon();
        String twoPages = PAGE.replace("\"totalCount\": 5", "\"totalCount\": 150");
        mockServer.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(twoPages, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("pageNo=2")))
                .andRespond(withSuccess(twoPages.replace("126508", "126510").replace("126509", "126511"),
                        MediaType.APPLICATION_JSON));

        seeder.seed(GANGWON);

        mockServer.verify();
        assertThat(placeRepository.findAll()).hasSize(4);
    }

    // ---------- 대표 이미지 (6-6에서 thumbnailUrl로 나간다) ----------

    @Test
    @DisplayName("대표 이미지가 있으면 place_media에 담는다")
    void savesThumbnailImage() {
        // 슬롯 응답의 thumbnailUrl이 여기서 나온다. 적재 때 담아 두지 않으면
        // 조회 시점에 채울 방법이 없다(목록 응답에만 오는 값이라 나중에 다시 받으려면 재적재해야 한다).
        seededGangwon();
        expectOnePage();

        seeder.seed(GANGWON);

        List<PlaceMedia> media = placeMediaRepository.findAll();
        assertThat(media).hasSize(1);
        assertThat(media.get(0).getMediaUrl()).isEqualTo("https://cdn.example.com/beach.jpg");
        assertThat(media.get(0).getMediaType()).isEqualTo(com.lottotrip.common.enums.MediaType.IMAGE);
        assertThat(media.get(0).getPlace().getContentId()).isEqualTo("126508");
    }

    @Test
    @DisplayName("이미지가 없는 장소는 담지 않는다 — 빈 행을 만들지 않는다")
    void skipsPlacesWithoutImage() {
        // 실측 채움률이 18%라 대부분의 장소에 이미지가 없다. 빈 문자열로 행을 만들면
        // media_url이 NOT NULL인데도 의미 없는 값이 쌓이고, 조회 때 빈 URL이 응답에 나간다.
        seededGangwon();
        expectOnePage();

        seeder.seed(GANGWON);

        // 담긴 장소는 2건(관광지·문화시설)인데 이미지가 있는 것은 1건뿐이다.
        assertThat(placeRepository.findAll()).hasSize(2);
        assertThat(placeMediaRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("두 번 돌려도 같은 이미지가 쌓이지 않는다")
    void doesNotDuplicateImageOnRerun() {
        // 적재는 여러 번 돌아간다(중간 실패 후 재실행, 정기 갱신).
        // 막지 않으면 돌릴 때마다 같은 이미지 행이 하나씩 늘어난다.
        seededGangwon();
        // 기대는 미리 다 걸어 둔다. MockRestServiceServer는 실제 요청이 한 번 나간 뒤에는
        // 새 기대를 받아 주지 않는다.
        mockServer.expect(ExpectedCount.twice(), requestTo(containsString("areaBasedList2")))
                .andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

        seeder.seed(GANGWON);
        seeder.seed(GANGWON);

        assertThat(placeMediaRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("응답이 끝없이 '더 있다'고 해도 페이지 상한에서 멈춘다")
    void stopsAtPageLimit() {
        // 바깥이 이상해져도 우리 배치가 일일 할당량을 통째로 태워서는 안 된다.
        // 실제로 그런 일이 있었다(2026-08-14): 범위 밖 페이지의 numOfRows가 0으로 오는 것을 몰라
        // hasNext()가 영원히 참이 되었고, areaBasedList2 1,000회를 다 쓰고서야 429로 멈췄다.
        // hasNext()는 고쳤지만, 바깥 응답이 또 달라져도 폭주 자체가 불가능해야 한다.
        seededGangwon();
        String neverEnding = PAGE.replace("\"totalCount\": 5", "\"totalCount\": 999999");
        mockServer.expect(ExpectedCount.times(PlaceSeeder.MAX_PAGES),
                        requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess(neverEnding, MediaType.APPLICATION_JSON));

        seeder.seed(GANGWON);

        // 정확히 상한만큼만 부르고 멈춘다. 한 번이라도 더 부르면 여기서 터진다.
        mockServer.verify();
    }

    @Test
    @DisplayName("빈 페이지를 받으면 전체 건수와 어긋나더라도 멈춘다")
    void stopsOnEmptyPage() {
        // totalCount는 999999인데 항목이 0건인 모순된 응답. 줄 것이 없다는 응답보다
        // 확실한 종료 신호는 없다. 이걸 무시하면 빈 페이지를 영원히 받아 간다.
        seededGangwon();
        mockServer.expect(ExpectedCount.once(), requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": { "resultCode": "0000", "resultMsg": "OK" },
                            "body": { "items": "", "numOfRows": 0, "pageNo": 1, "totalCount": 999999 }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        seeder.seed(GANGWON);

        mockServer.verify();
        assertThat(placeRepository.findAll()).isEmpty();
    }
}
