package com.lottotrip.slot.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.mission.service.MissionMatcher;
import com.lottotrip.mission.service.TemplateMissionGenerator;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.place.service.PlaceUpserter;
import com.lottotrip.place.service.RealtimePlaceFinder;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiProperties;
import com.lottotrip.place.tourapi.TravelCategoryMapper;
import com.lottotrip.slot.dto.SlotDrawRequest;
import com.lottotrip.slot.dto.SlotDrawResponse;
import com.lottotrip.slot.entity.TransportType;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 슬롯 돌리기 전체 흐름 검증. (roadmap 6-13, 결정 12)
 *
 * 세션 확보(6-1) → 반경(6-2) → 실시간 조회·추첨(6-11) → 장소 저장(6-12)
 * → 미션 매칭(6-5) → `saved_slots` 저장까지가 한 흐름이다.
 *
 * ⚠️ 결정 12로 크게 달라진 지점. 예전에는 후보를 DB에 미리 심어 두고 뽑았지만,
 * 이제는 매번 TourAPI에서 받아 온다. 그래서 이 테스트는 DB에 장소를 심지 않고 응답을 흉내 낸다.
 *
 * DB는 진짜를 쓴다. `saved_slots`가 실제로 저장돼야 `slotId`가 나오고,
 * 그 값이 7단계 코스 추가의 입력이 된다. 바깥 호출만 {@link MockRestServiceServer}로 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SlotDrawServiceTest extends PostgresContainerSupport {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
    private static final String WITH_BASE_URL = "https://apis.data.go.kr/B551011/KorWithService2";

    private static final double CENTER_LAT = 37.7519;
    private static final double CENTER_LNG = 128.8761;

    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private SavedSlotRepository savedSlotRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private PlaceMediaRepository placeMediaRepository;
    @Autowired
    private MissionRepository missionRepository;
    @Autowired
    private StateRepository stateRepository;
    @Autowired
    private CityRepository cityRepository;

    private MockRestServiceServer mockServer;
    private SlotService slotService;
    private User user;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder,
                new TourApiProperties(BASE_URL, WITH_BASE_URL, "test-key", null, null, 100));

        slotService = new SlotService(
                tripSessionRepository,
                userRepository,
                new RealtimePlaceFinder(client),
                new PlaceUpserter(placeRepository, placeMediaRepository,
                        stateRepository, cityRepository, new TravelCategoryMapper()),
                new MissionMatcher(missionRepository, new TemplateMissionGenerator()),
                savedSlotRepository,
                event -> { });
        user = userRepository.save(User.create("a@test.com", "테스터", null));
    }

    private SlotDrawRequest walkRequest() {
        return new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "walk", null, null);
    }

    /**
     * 후보 1건짜리 응답. 후보를 하나만 두어 결과를 결정적으로 만든다 —
     * 이 테스트들은 "무엇이 뽑히는가"가 아니라 "흐름이 이어지는가"를 본다(추첨 자체는 6-11에서 검증했다).
     */
    private static String oneCandidate(String title, String firstImage, String dist) {
        return """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": { "item": [ {
                        "contentid": "126508", "contenttypeid": "12",
                        "title": "%s", "addr1": "강원특별자치도 강릉시 사천면", "addr2": "",
                        "areacode": "32", "sigungucode": "1",
                        "cat1": "A01", "cat2": "A0101", "cat3": "A01011200",
                        "dist": "%s", "firstimage": "%s", "firstimage2": "",
                        "mapx": "128.8954", "mapy": "37.8021",
                        "modifiedtime": "20240115103045"
                      } ] },
                      "numOfRows": 1000, "pageNo": 1, "totalCount": 1
                    }
                  }
                }
                """.formatted(title, dist, firstImage);
    }

    private static final String NO_CANDIDATE = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": { "items": "", "numOfRows": 1000, "pageNo": 1, "totalCount": 0 }
              }
            }
            """;

    private void expectDraw(String body) {
        mockServer.expect(requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("실시간으로 받은 장소를 뽑아 슬롯을 만든다")
    void drawsPlaceFromLiveApi() {
        expectDraw(oneCandidate("사천진해변", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.slotId()).isNotNull();
        assertThat(response.place().name()).isEqualTo("사천진해변");
        assertThat(response.place().latitude()).isEqualTo(37.8021);
        mockServer.verify();
    }

    @Test
    @DisplayName("뽑은 장소를 places에 담는다 — 슬롯·미션이 이어 붙을 곳이 필요하다")
    void savesDrawnPlace() {
        // 결정 12로 되살아난 단계다. 배치 시절에는 이미 담겨 있어 저장할 일이 없었다.
        expectDraw(oneCandidate("사천진해변", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(placeRepository.findByContentId("126508")).isPresent();
        assertThat(response.place().placeId()).isNotNull();
    }

    @Test
    @DisplayName("카테고리는 TourAPI 표시명으로 나간다")
    void exposesCategoryAsDisplayName() {
        // DB에는 NATURE_ATTRACTION으로 저장하지만 응답에는 "자연관광지"로 나가야 한다.
        // ⚠️ 예전에는 해수욕장이 "해변"으로 나갔다 — cat3라서 cat2 체계에서는 묻힌다(결정 16).
        expectDraw(oneCandidate("사천진해변", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().category()).isEqualTo("자연관광지");
    }

    @Test
    @DisplayName("거리는 API가 준 dist를 쓴다 — 우리가 계산하지 않는다")
    void usesDistanceFromApi() {
        // 결정 12의 이점이다. 배치 시절에는 DB에 거리가 없어 Haversine을 돌려야 했다.
        // 1113m → 1.1km. 소수점을 그대로 흘리면 응답에 의미 없는 자릿수가 나간다.
        expectDraw(oneCandidate("사천진해변", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().distanceKm()).isCloseTo(1.1, within(0.001));
    }

    @Test
    @DisplayName("뽑은 결과를 saved_slots에 남긴다 — 코스 추가가 이 slotId를 쓴다")
    void persistsSavedSlot() {
        expectDraw(oneCandidate("사천진해변", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(savedSlotRepository.findById(response.slotId())).isPresent()
                .get()
                .satisfies(slot -> {
                    assertThat(slot.getPlace().getContentId()).isEqualTo("126508");
                    assertThat(slot.getSession().getUser().getId()).isEqualTo(user.getId());
                });
    }

    @Test
    @DisplayName("제시한 미션을 슬롯에 함께 남긴다 (결정 14)")
    void persistsPresentedMission() {
        // 남기지 않으면 결과 조회가 다른 미션을 돌려준다. 2026-08-15에 실제로 재현된 버그다.
        expectDraw(oneCandidate("사천진해변", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(savedSlotRepository.findById(response.slotId())).isPresent()
                .get()
                .satisfies(slot -> assertThat(slot.getMission().getId())
                        .isEqualTo(response.mission().missionId()));
    }

    @Test
    @DisplayName("미션을 함께 준다 — 없으면 만들어서라도 채운다")
    void attachesMission() {
        expectDraw(oneCandidate("사천진해변", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.mission()).isNotNull();
        assertThat(response.mission().missionId()).isNotNull();
        assertThat(response.mission().title()).isNotBlank();
    }

    @Test
    @DisplayName("대표 이미지는 목록 응답에서 바로 나온다 — place_media를 다시 조회하지 않는다")
    void exposesThumbnailFromApiResponse() {
        expectDraw(oneCandidate("사천진해변", "https://cdn.example.com/beach.jpg", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().thumbnailUrl()).isEqualTo("https://cdn.example.com/beach.jpg");
    }

    @Test
    @DisplayName("대표 이미지가 없으면 thumbnailUrl은 null이다")
    void allowsMissingThumbnail() {
        expectDraw(oneCandidate("이미지 없는 곳", "", "1113.0"));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().thumbnailUrl()).isNull();
    }

    // ---------- 요청 조건 ----------

    @Test
    @DisplayName("무장애를 요청하면 무장애 서비스에서 뽑는다")
    void drawsFromAccessibleService() {
        mockServer.expect(requestTo(containsString(WITH_BASE_URL)))
                .andRespond(withSuccess(oneCandidate("무장애 관광지", "", "500.0"),
                        MediaType.APPLICATION_JSON));

        SlotDrawResponse response = slotService.draw(user.getId(),
                new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "walk", true, null));

        assertThat(response.place().name()).isEqualTo("무장애 관광지");
        mockServer.verify();
    }

    @Test
    @DisplayName("종류를 지정하면 그 종류만 요청한다")
    void appliesContentTypeFilter() {
        mockServer.expect(requestTo(containsString("contentTypeId=39")))
                .andRespond(withSuccess(oneCandidate("맛집", "", "500.0"), MediaType.APPLICATION_JSON));

        slotService.draw(user.getId(),
                new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "walk", null, "39"));

        mockServer.verify();
    }

    @Test
    @DisplayName("이동수단에 따라 반경이 달라진다 — walk 10km / car 30km")
    void radiusFollowsTransport() {
        // ⚠️ MockRestServiceServer는 기대를 먼저 전부 걸어 둬야 한다.
        //    첫 요청이 나간 뒤에 추가하면 "Cannot add more expectations"로 거절한다.
        mockServer.expect(requestTo(containsString("radius=10000")))
                .andRespond(withSuccess(oneCandidate("가까운 곳", "", "500.0"), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("radius=30000")))
                .andRespond(withSuccess(oneCandidate("먼 곳", "", "25000.0"), MediaType.APPLICATION_JSON));

        slotService.draw(user.getId(), walkRequest());

        // 세션이 walk로 이미 만들어졌으므로 다른 회원으로 확인한다.
        User carUser = userRepository.save(User.create("b@test.com", "차", null));
        slotService.draw(carUser.getId(),
                new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "car", null, null));

        mockServer.verify();
    }

    // ---------- 세션 ----------

    @Test
    @DisplayName("연달아 돌리면 같은 세션에 쌓인다")
    void reusesSessionAcrossDraws() {
        expectDraw(oneCandidate("A", "", "500.0"));
        expectDraw(oneCandidate("A", "", "500.0"));

        slotService.draw(user.getId(), walkRequest());
        slotService.draw(user.getId(), walkRequest());

        assertThat(tripSessionRepository.findAll()).hasSize(1);
        assertThat(savedSlotRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("세션이 있어도 요청 좌표로 뽑는다 — 여행 중 숙소를 옮기면 반영돼야 한다 (결정 21)")
    void usesRequestCoordinatesEvenWhenSessionExists() {
        // 예전에는 12시간 이내 세션이 있으면 그 세션의 첫 좌표로 뽑아서,
        // 서울로 이동해 돌려도 강릉 장소가 나왔다(2026-08-15 실측으로 재현).
        mockServer.expect(requestTo(containsString("mapY=" + CENTER_LAT)))
                .andRespond(withSuccess(oneCandidate("강릉", "", "500.0"), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("mapY=37.5665")))
                .andRespond(withSuccess(oneCandidate("서울", "", "800.0"), MediaType.APPLICATION_JSON));

        slotService.draw(user.getId(), walkRequest());  // 세션이 강릉 좌표로 생성된다
        slotService.draw(user.getId(), new SlotDrawRequest(37.5665, 126.9780, 50_000, "walk", null, null));

        mockServer.verify();
    }

    @Test
    @DisplayName("세션이 있어도 요청 이동수단의 반경으로 뽑는다 — 차를 빌리면 반영돼야 한다 (결정 21)")
    void usesRequestTransportEvenWhenSessionExists() {
        mockServer.expect(requestTo(containsString("radius=10000")))
                .andRespond(withSuccess(oneCandidate("가까운 곳", "", "500.0"), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("radius=30000")))
                .andRespond(withSuccess(oneCandidate("먼 곳", "", "25000.0"), MediaType.APPLICATION_JSON));

        slotService.draw(user.getId(), walkRequest());  // 세션이 walk(10km)로 생성된다
        slotService.draw(user.getId(),
                new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "car", null, null));

        mockServer.verify();
    }

    @Test
    @DisplayName("세션 값은 갱신되지 않는다 — 그 여행의 시작 조건 기록이다 (결정 1 유지)")
    void doesNotUpdateSessionOnReuse() {
        expectDraw(oneCandidate("A", "", "500.0"));
        expectDraw(oneCandidate("B", "", "800.0"));

        slotService.draw(user.getId(), walkRequest());
        slotService.draw(user.getId(), new SlotDrawRequest(37.5665, 126.9780, 50_000, "car", null, null));

        // 추첨에는 새 값을 썼지만, 세션에 남는 것은 처음 시작 조건이다.
        TripSession session = tripSessionRepository.findAll().get(0);
        assertThat(session.getAccommodationLatitude()).isEqualTo(CENTER_LAT);
        assertThat(session.getAccommodationLongitude()).isEqualTo(CENTER_LNG);
        assertThat(session.getTransportation()).isEqualTo(TransportType.WALK);
        assertThat(session.getSearchRadiusKm()).isEqualTo(TransportType.WALK.getSearchRadiusKm());
    }

    @Test
    @DisplayName("세션이 이미 있어도 잘못된 이동수단은 400 — 조용히 통과하면 안 된다")
    void rejectsUnknownTransportEvenWhenSessionExists() {
        // 예전에는 세션이 있으면 TransportType.from()이 아예 호출되지 않아
        // "bike" 같은 값이 400 없이 통과했다. 요청 값을 쓰기로 한 이상 매 요청 검증해야 한다.
        expectDraw(oneCandidate("A", "", "500.0"));
        slotService.draw(user.getId(), walkRequest());  // 세션 생성

        assertThatThrownBy(() -> slotService.draw(
                user.getId(), new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "bike", null, null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

        mockServer.verify();  // 두 번째 요청은 바깥을 부르지 않는다
    }

    @Test
    @DisplayName("같은 장소가 또 뽑혀도 places에 중복 저장하지 않는다")
    void doesNotDuplicatePlaceAcrossDraws() {
        // 온디맨드에서는 인기 있는 장소가 반복해서 뽑힌다.
        expectDraw(oneCandidate("사천진해변", "", "500.0"));
        expectDraw(oneCandidate("사천진해변", "", "500.0"));

        slotService.draw(user.getId(), walkRequest());
        slotService.draw(user.getId(), walkRequest());

        assertThat(placeRepository.findAll()).hasSize(1);
        assertThat(savedSlotRepository.findAll()).hasSize(2);
    }

    // ---------- 오류 ----------

    @Test
    @DisplayName("반경 안에 후보가 없으면 NO_PLACE_FOUND")
    void failsWhenNoCandidate() {
        expectDraw(NO_CANDIDATE);

        assertThatThrownBy(() -> slotService.draw(user.getId(), walkRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_PLACE_FOUND);
    }

    @Test
    @DisplayName("후보가 없으면 슬롯도 장소도 저장하지 않는다")
    void savesNothingWhenNoCandidate() {
        expectDraw(NO_CANDIDATE);

        assertThatThrownBy(() -> slotService.draw(user.getId(), walkRequest()))
                .isInstanceOf(CustomException.class);

        assertThat(savedSlotRepository.findAll()).isEmpty();
        assertThat(placeRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("정의되지 않은 이동수단은 400 — 바깥을 부르기 전에 끝난다")
    void rejectsUnknownTransport() {
        // 이동수단 해석이 실패하면 세션조차 만들어지지 않으므로 API 호출이 나가면 안 된다.
        // mockServer에 아무 기대도 걸지 않았으므로, 호출이 나가면 이 테스트가 실패한다.
        assertThatThrownBy(() -> slotService.draw(
                user.getId(), new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "bike", null, null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

        mockServer.verify();
    }

    @Test
    @DisplayName("탈퇴한 회원의 토큰이면 401 — 바깥을 부르기 전에 끝난다")
    void rejectsMissingUser() {
        assertThatThrownBy(() -> slotService.draw(999_999L, walkRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        mockServer.verify();
    }

    @Test
    @DisplayName("draw 한 번에 공공 API를 한 번만 부른다")
    void callsApiOnlyOncePerDraw() {
        // 할당량이 오퍼레이션당 하루 1,000회다. draw당 호출이 늘면 하루 draw 수가 그만큼 준다.
        mockServer.expect(once(), requestTo(containsString("/locationBasedList2")))
                .andRespond(withSuccess(oneCandidate("사천진해변", "", "1113.0"),
                        MediaType.APPLICATION_JSON));

        slotService.draw(user.getId(), walkRequest());

        mockServer.verify();
    }
}
