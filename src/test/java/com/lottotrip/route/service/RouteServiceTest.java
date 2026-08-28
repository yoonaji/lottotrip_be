package com.lottotrip.route.service;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.route.dto.CarRouteResponse;
import com.lottotrip.route.dto.RouteResponse;
import com.lottotrip.route.navermap.NaverDirectionsClient;
import com.lottotrip.route.navermap.NaverDirectionsProperties;
import com.lottotrip.route.odsay.OdsayClient;
import com.lottotrip.route.odsay.OdsayProperties;
import com.lottotrip.slot.entity.SavedSlot;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 슬롯 결과 장소까지의 대중교통 경로 조회 검증. {@code SlotResultServiceTest}와 같은 구조다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RouteServiceTest extends PostgresContainerSupport {

    private static final String ROUTE_RESPONSE = """
            {
              "result": {
                "path": [
                  {
                    "pathType": 2,
                    "info": { "totalTime": 45, "payment": 1500, "totalWalk": 300,
                              "totalDistance": 12000, "busTransitCount": 1, "subwayTransitCount": 0 },
                    "subPath": [
                      { "trafficType": 2, "distance": 8000, "sectionTime": 21, "stationCount": 13,
                        "startName": "출발 정류장", "endName": "강변역(B)",
                        "lane": [ { "busNo": "92" }, { "busNo": "9" } ] }
                    ]
                  }
                ]
              }
            }
            """;

    private static final String NO_ROUTE_RESPONSE = """
            { "error": { "code": "-99", "message": "검색결과가 없습니다" } }
            """;

    private static final String CAR_ROUTE_RESPONSE = """
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

    private static final String CAR_NO_ROUTE_RESPONSE = """
            { "code": 1, "message": "경로를 찾을 수 없습니다", "route": {} }
            """;

    @Autowired
    private SavedSlotRepository savedSlotRepository;
    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;

    private MockRestServiceServer mockServer;
    private RouteService routeService;
    private User user;
    private Place place;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        OdsayClient odsayClient = new OdsayClient(builder,
                new OdsayProperties("https://api.odsay.com/v1/api", "test-key"));
        NaverDirectionsClient naverDirectionsClient = new NaverDirectionsClient(builder,
                new NaverDirectionsProperties(
                        "https://naveropenapi.apigw.ntruss.com/map-direction/v1", "test-key-id", "test-key"));
        routeService = new RouteService(savedSlotRepository, odsayClient, naverDirectionsClient);

        user = userRepository.save(User.create("a@test.com", "테스터", null));
        place = placeRepository.save(Place.builder()
                .contentId("126508")
                .contentTypeId("12")
                .name("사천진해변")
                .category(TravelCategory.NATURE_ATTRACTION)
                .address("강원특별자치도 강릉시 사천면")
                .latitude(37.8021)
                .longitude(128.8954)
                .build());
    }

    private SavedSlot savedSlotOf(User owner, Double accommodationLatitude, Double accommodationLongitude) {
        TripSession session = tripSessionRepository.save(TripSession.create(
                owner, BudgetLevel.MEDIUM, TransportType.WALK,
                accommodationLatitude, accommodationLongitude));
        return savedSlotRepository.save(SavedSlot.create(session, place, null));
    }

    private void expectRouteCall(String response) {
        mockServer.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("숙소 좌표에서 당첨 장소까지의 경로를 돌려준다")
    void returnsRouteFromAccommodationToPlace() {
        SavedSlot slot = savedSlotOf(user, 37.7519, 128.8761);
        expectRouteCall(ROUTE_RESPONSE);

        RouteResponse response = routeService.getTransitRoute(user.getId(), slot.getId());

        assertThat(response.totalMinutes()).isEqualTo(45);
        assertThat(response.legs()).hasSize(1);
        assertThat(response.legs().get(0).mode()).isEqualTo("BUS");
        assertThat(response.legs().get(0).routeName()).isEqualTo("92, 9");
        mockServer.verify();
    }

    @Test
    @DisplayName("숙소 좌표를 출발지로, 장소 좌표를 도착지로 보낸다")
    void sendsAccommodationAsStartAndPlaceAsEnd() {
        SavedSlot slot = savedSlotOf(user, 37.7519, 128.8761);
        mockServer.expect(requestTo(containsString("SX=128.8761")))
                .andExpect(requestTo(containsString("SY=37.7519")))
                .andExpect(requestTo(containsString("EX=128.8954")))
                .andExpect(requestTo(containsString("EY=37.8021")))
                .andRespond(withSuccess(ROUTE_RESPONSE, MediaType.APPLICATION_JSON));

        routeService.getTransitRoute(user.getId(), slot.getId());

        mockServer.verify();
    }

    @Test
    @DisplayName("자동차 경로도 숙소 좌표에서 당첨 장소까지로 조회한다")
    void returnsCarRouteFromAccommodationToPlace() {
        SavedSlot slot = savedSlotOf(user, 37.7519, 128.8761);
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withSuccess(CAR_ROUTE_RESPONSE, MediaType.APPLICATION_JSON));

        CarRouteResponse response = routeService.getCarRoute(user.getId(), slot.getId());

        assertThat(response.totalMinutes()).isEqualTo(27); // 1,620,000ms / 60,000
        assertThat(response.totalDistanceMeters()).isEqualTo(11069.0);
        assertThat(response.taxiFare()).isEqualTo(12500);
        mockServer.verify();
    }

    // ---------- 오류 ----------

    @Test
    @DisplayName("없는 슬롯이면 RESULT_NOT_FOUND")
    void failsWhenSlotMissing() {
        assertThatThrownBy(() -> routeService.getTransitRoute(user.getId(), 999_999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("자동차 경로도 없는 슬롯이면 RESULT_NOT_FOUND")
    void failsCarRouteWhenSlotMissing() {
        assertThatThrownBy(() -> routeService.getCarRoute(user.getId(), 999_999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 슬롯은 조회할 수 없다")
    void failsWhenSlotBelongsToAnotherUser() {
        User other = userRepository.save(User.create("b@test.com", "남", null));
        SavedSlot slot = savedSlotOf(other, 37.7519, 128.8761);

        assertThatThrownBy(() -> routeService.getTransitRoute(user.getId(), slot.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
        mockServer.verify(); // 기대를 걸지 않았으므로 ODsay 호출이 있었다면 여기서 터진다
    }

    @Test
    @DisplayName("탈퇴로 숙소 좌표가 지워진 세션은 ROUTE_NOT_FOUND — 출발지를 모른다")
    void failsWhenAccommodationCoordinateErased() {
        SavedSlot slot = savedSlotOf(user, null, null);

        assertThatThrownBy(() -> routeService.getTransitRoute(user.getId(), slot.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTE_NOT_FOUND);
        mockServer.verify();
    }

    @Test
    @DisplayName("ODsay가 경로 없음을 주면 그대로 ROUTE_NOT_FOUND로 전달한다")
    void propagatesRouteNotFound() {
        SavedSlot slot = savedSlotOf(user, 37.7519, 128.8761);
        expectRouteCall(NO_ROUTE_RESPONSE);

        assertThatThrownBy(() -> routeService.getTransitRoute(user.getId(), slot.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("네이버가 경로 없음을 주면 그대로 ROUTE_NOT_FOUND로 전달한다")
    void propagatesCarRouteNotFound() {
        SavedSlot slot = savedSlotOf(user, 37.7519, 128.8761);
        mockServer.expect(requestTo(containsString("/driving")))
                .andRespond(withSuccess(CAR_NO_ROUTE_RESPONSE, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> routeService.getCarRoute(user.getId(), slot.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTE_NOT_FOUND);
    }
}
