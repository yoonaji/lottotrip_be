package com.lottotrip.place.service;

import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiPlaceItem;
import com.lottotrip.place.tourapi.TourApiProperties;
import com.lottotrip.place.tourapi.TourApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실시간 후보 선정기 검증. (roadmap 6-11, 결정 12)
 *
 * **슬롯 추첨의 전부가 여기서 일어난다.** 결정 12로 배치가 없어지면서, 어떤 장소가 사용자에게
 * 보이는지는 이 클래스가 어느 후보를 받아 오고 그중 무엇을 고르는가로 결정된다.
 *
 * 바깥 호출은 {@link MockRestServiceServer}로 막는다. 공공 API 상태나 일일 할당량에
 * 테스트가 좌우되면 안 된다 — 2026-08-14에 할당량을 태우고 배운 것이다.
 */
class RealtimePlaceFinderTest {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
    private static final String WITH_BASE_URL = "https://apis.data.go.kr/B551011/KorWithService2";

    private static final double LAT = 37.7519;
    private static final double LNG = 128.8761;

    private MockRestServiceServer mockServer;
    private TourApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TourApiClient(builder,
                new TourApiProperties(BASE_URL, WITH_BASE_URL, "test-key", null, null, 100));
    }

    /**
     * 난수를 고정한 선정기.
     *
     * 추첨은 난수에 기대므로 그대로 두면 **테스트가 실행할 때마다 다른 답을 낸다.**
     * "몇 번째를 고를지"를 우리가 정해 두면 결과를 단정할 수 있다.
     */
    private RealtimePlaceFinder finderPicking(int index) {
        return new RealtimePlaceFinder(client, () -> new FixedRandom(index));
    }

    /** `nextInt(bound)`가 항상 정해진 값을 주는 난수원. 나머지 기능은 쓰지 않는다. */
    private record FixedRandom(int value) implements RandomGenerator {
        @Override
        public int nextInt(int bound) {
            return value;
        }

        @Override
        public long nextLong() {
            return value;
        }
    }

    /** 항목 `count`개짜리 정상 응답. 제목은 place-1 … place-N으로 구분한다. */
    private static String listOf(int count) {
        String items = IntStream.rangeClosed(1, count)
                .mapToObj(i -> """
                        {
                          "contentid": "%d", "contenttypeid": "12",
                          "title": "place-%d", "addr1": "강원특별자치도 강릉시", "addr2": "",
                          "areacode": "32", "sigungucode": "1",
                          "cat1": "A01", "cat2": "A0101", "cat3": "A01011200",
                          "dist": "%d.0", "firstimage": "", "firstimage2": "",
                          "mapx": "128.88", "mapy": "37.80", "modifiedtime": "20240115103045"
                        }""".formatted(1000 + i, i, i * 100))
                .collect(Collectors.joining(","));

        return """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": { "item": [ %s ] },
                      "numOfRows": 1000, "pageNo": 1, "totalCount": %d
                    }
                  }
                }
                """.formatted(items, count);
    }

    private static final String EMPTY = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": { "items": "", "numOfRows": 1000, "pageNo": 1, "totalCount": 0 }
              }
            }
            """;

    // ---------- 호출 방식 ----------

    @Test
    @DisplayName("한 번만 호출한다 — 페이지를 넘기지 않는다")
    void callsApiExactlyOnce() {
        // 2026-08-14에 페이지 순회가 끝나지 않아 일일 할당량 1,000회를 통째로 태웠다.
        // 순회를 아예 하지 않으면 그 사고가 구조적으로 불가능해진다. once()가 이를 고정한다.
        mockServer.expect(once(), requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(listOf(3), MediaType.APPLICATION_JSON));

        finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null);

        mockServer.verify();
    }

    @Test
    @DisplayName("한 번에 1,000건을 요청한다 — API가 허용하는 최대다")
    void requestsMaximumPageSize() {
        // 후보 전체를 한 번에 받아야 그중에서 고를 수 있다. 100건만 받으면
        // 반경 안에 880곳이 있어도 가까운 100곳에서만 뽑히게 된다.
        mockServer.expect(requestTo(containsString("numOfRows=1000")))
                .andRespond(withSuccess(listOf(3), MediaType.APPLICATION_JSON));

        finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null);

        mockServer.verify();
    }

    @Test
    @DisplayName("반경을 km에서 m로 바꿔 보낸다 — 우리는 km로 말하고 API는 m로 받는다")
    void convertsRadiusToMeters() {
        mockServer.expect(requestTo(containsString("radius=30000")))
                .andRespond(withSuccess(listOf(3), MediaType.APPLICATION_JSON));

        finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null);

        mockServer.verify();
    }

    @Test
    @DisplayName("무장애를 요청하면 무장애 서비스에서 후보를 받는다")
    void usesAccessibleService() {
        // 무장애 서비스에 담긴 장소는 국문 관광정보의 부분집합이라,
        // 이쪽으로 조회하는 것 자체가 "무장애 정보가 등록된 곳만"이라는 필터가 된다.
        mockServer.expect(requestTo(containsString(WITH_BASE_URL)))
                .andRespond(withSuccess(listOf(3), MediaType.APPLICATION_JSON));

        finderPicking(0).drawOne(TourApiService.ACCESSIBLE, LAT, LNG, 30, null);

        mockServer.verify();
    }

    @Test
    @DisplayName("종류를 지정하면 그 종류만 요청한다")
    void appliesContentTypeFilter() {
        mockServer.expect(requestTo(containsString("contentTypeId=12")))
                .andRespond(withSuccess(listOf(3), MediaType.APPLICATION_JSON));

        finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, "12");

        mockServer.verify();
    }

    @Test
    @DisplayName("종류를 지정하지 않으면 전 종류를 받는다 (결정 13)")
    void omitsContentTypeFilterByDefault() {
        mockServer.expect(requestTo(not(containsString("contentTypeId"))))
                .andRespond(withSuccess(listOf(3), MediaType.APPLICATION_JSON));

        finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null);

        mockServer.verify();
    }

    // ---------- 추첨 ----------

    @Test
    @DisplayName("받은 후보 중 난수가 가리키는 하나를 돌려준다")
    void picksCandidateAtRandomIndex() {
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(listOf(5), MediaType.APPLICATION_JSON));

        Optional<TourApiPlaceItem> picked = finderPicking(2).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null);

        assertThat(picked).isPresent();
        assertThat(picked.get().title()).isEqualTo("place-3"); // 0-based 인덱스 2 = 3번째
    }

    @Test
    @DisplayName("첫 후보도 마지막 후보도 뽑힐 수 있다 — 받아온 전부가 대상이다")
    void everyCandidateIsReachable() {
        // 받아왔는데 뽑힐 수 없는 후보가 있으면, 그 장소는 사용자에게 영영 보이지 않는다.
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(listOf(5), MediaType.APPLICATION_JSON));
        assertThat(finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null))
                .get().extracting(TourApiPlaceItem::title).isEqualTo("place-1");

        setUp();
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(listOf(5), MediaType.APPLICATION_JSON));
        assertThat(finderPicking(4).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null))
                .get().extracting(TourApiPlaceItem::title).isEqualTo("place-5");
    }

    @Test
    @DisplayName("난수 범위는 받은 후보 수다 — 빈자리를 가리키지 않는다")
    void boundsRandomByCandidateCount() {
        // nextInt(bound)의 bound가 후보 수보다 크면 목록 밖을 가리켜 예외가 난다.
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(listOf(3), MediaType.APPLICATION_JSON));

        RealtimePlaceFinder finder = new RealtimePlaceFinder(client, () -> new RandomGenerator() {
            @Override
            public int nextInt(int bound) {
                assertThat(bound).isEqualTo(3); // 받은 후보 수와 같아야 한다
                return bound - 1;
            }

            @Override
            public long nextLong() {
                return 0;
            }
        });

        assertThat(finder.drawOne(TourApiService.KOREAN, LAT, LNG, 30, null))
                .get().extracting(TourApiPlaceItem::title).isEqualTo("place-3");
    }

    // ---------- 후보 없음 ----------

    @Test
    @DisplayName("반경 안에 아무것도 없으면 빈 값이다 — 404로 바꿀지는 부르는 쪽이 정한다")
    void returnsEmptyWhenNoCandidate() {
        // 여기서 NO_PLACE_FOUND를 던지면 이 클래스가 HTTP 응답까지 아는 셈이 된다.
        // "없다"는 사실만 전하고 판단은 6-13에 맡긴다. (6-3의 원칙을 그대로 잇는다)
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        assertThat(finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null)).isEmpty();
    }

    @Test
    @DisplayName("좌표가 없는 후보는 뽑기 전에 걸러낸다 — 뽑아도 저장할 수 없다")
    void excludesCandidateWithoutCoordinate() {
        // places.latitude·longitude는 NOT NULL이다. 좌표 없는 것을 뽑으면
        // 추첨은 성공했는데 저장에서 터지는, 되돌리기 곤란한 상태가 된다.
        String withHole = listOf(2).replace("\"mapx\": \"128.88\", \"mapy\": \"37.80\"",
                "\"mapx\": \"\", \"mapy\": \"\"");
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(withHole, MediaType.APPLICATION_JSON));

        // 2건 중 좌표 있는 것이 하나도 없으면 후보가 비어야 한다
        assertThat(finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null)).isEmpty();
    }

    // ---------- 숙박 제외 (roadmap 6-16, 결정 18) ----------

    @Test
    @DisplayName("숙박(B0201)은 후보에서 뺀다 — 숙소는 사용자가 따로 입력한다")
    void excludesLodging() {
        // 결정 13(전 종류 포괄)의 부작용이었다. 강릉 30km 후보 880건 중 190건(22%)이 모텔·호텔이라
        // 룰렛을 다섯 번 돌리면 한 번은 "오늘의 여행지: OO모텔"이 나왔다.
        String lodging = listOf(2)
                .replace("\"cat1\": \"A01\", \"cat2\": \"A0101\", \"cat3\": \"A01011200\"",
                        "\"cat1\": \"B02\", \"cat2\": \"B0201\", \"cat3\": \"B02010100\"")
                .replace("\"contenttypeid\": \"12\"", "\"contenttypeid\": \"32\"");
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(lodging, MediaType.APPLICATION_JSON));

        // 2건 다 숙박이면 뽑을 것이 남지 않는다
        assertThat(finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null)).isEmpty();
    }

    @Test
    @DisplayName("cat2가 비어도 관광타입 32면 숙박으로 보고 뺀다")
    void excludesLodgingByContentType() {
        // cat2는 실측 880건 중 빈 값이 0건이었지만, 비면 조용히 통과해 버린다.
        // 숙박은 관광타입(32)으로도 식별되므로 둘 중 하나만 맞아도 뺀다.
        String lodging = listOf(2)
                .replace("\"cat1\": \"A01\", \"cat2\": \"A0101\", \"cat3\": \"A01011200\"",
                        "\"cat1\": \"\", \"cat2\": \"\", \"cat3\": \"\"")
                .replace("\"contenttypeid\": \"12\"", "\"contenttypeid\": \"32\"");
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(lodging, MediaType.APPLICATION_JSON));

        assertThat(finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null)).isEmpty();
    }

    @Test
    @DisplayName("숙박이 섞여 있어도 나머지 후보는 그대로 뽑힌다")
    void keepsNonLodgingCandidates() {
        // 첫 항목만 숙박으로 바꾼다. 남는 것은 place-2·place-3뿐이므로
        // 인덱스 0이 가리키는 것은 place-1이 아니라 place-2여야 한다.
        String mixed = listOf(3).replaceFirst(
                "\"cat1\": \"A01\", \"cat2\": \"A0101\", \"cat3\": \"A01011200\"",
                "\"cat1\": \"B02\", \"cat2\": \"B0201\", \"cat3\": \"B02010100\"");
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(mixed, MediaType.APPLICATION_JSON));

        assertThat(finderPicking(0).drawOne(TourApiService.KOREAN, LAT, LNG, 30, null))
                .get().extracting(TourApiPlaceItem::title).isEqualTo("place-2");
    }

    @Test
    @DisplayName("난수 범위는 숙박을 뺀 뒤의 후보 수다")
    void boundsRandomAfterExcludingLodging() {
        // 거른 뒤의 수로 좁히지 않으면 목록 밖을 가리켜 예외가 난다.
        String mixed = listOf(3).replaceFirst(
                "\"cat1\": \"A01\", \"cat2\": \"A0101\", \"cat3\": \"A01011200\"",
                "\"cat1\": \"B02\", \"cat2\": \"B0201\", \"cat3\": \"B02010100\"");
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(mixed, MediaType.APPLICATION_JSON));

        RealtimePlaceFinder finder = new RealtimePlaceFinder(client, () -> new RandomGenerator() {
            @Override
            public int nextInt(int bound) {
                assertThat(bound).isEqualTo(2); // 3건 중 숙박 1건을 뺀 수
                return 0;
            }

            @Override
            public long nextLong() {
                return 0;
            }
        });

        assertThat(finder.drawOne(TourApiService.KOREAN, LAT, LNG, 30, null)).isPresent();
    }

    @Test
    @DisplayName("후보가 없으면 난수를 아예 쓰지 않는다")
    void doesNotDrawWhenEmpty() {
        // nextInt(0)은 IllegalArgumentException이다. 빈 목록을 먼저 걸러야 한다.
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        RealtimePlaceFinder finder = new RealtimePlaceFinder(client, () -> {
            throw new AssertionError("후보가 없는데 난수를 뽑으려 했다");
        });

        assertThat(finder.drawOne(TourApiService.KOREAN, LAT, LNG, 30, null)).isEmpty();
    }
}
