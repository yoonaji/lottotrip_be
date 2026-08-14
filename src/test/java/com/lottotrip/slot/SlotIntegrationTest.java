package com.lottotrip.slot;

import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.PlaceMedia;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 슬롯 도메인 통합 검증. (roadmap 6-8)
 *
 * <p>서비스 단위 테스트(6-1~6-7)가 각 부품의 동작을 이미 덮고 있으므로, 여기서는
 * <b>HTTP 경계에서만 드러나는 것</b>을 본다 — 인증이 실제로 걸리는가, 요청 본문 검증이 도는가,
 * 공통 응답 포맷과 에러 코드가 명세대로 나가는가.
 *
 * <p><b>바깥 호출은 나가지 않는다.</b> 인증키를 빈 값으로 두면 {@code TourApiClient}가
 * 네트워크를 타기 전에 멈추고, {@code PlaceDetailService}가 그 실패를 삼켜
 * {@code liveDetailLoaded=false}로 응답한다. 테스트가 공공 API 상태에 좌우되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "tourapi.service-key=")
@Transactional
class SlotIntegrationTest extends PostgresContainerSupport {

    private static final String DRAW_PATH = "/api/v1/slot/draw";
    private static final String RESULT_PATH = "/api/v1/slot/results/";

    /** 강릉시청 부근. 요청 좌표이자 후보 장소의 기준점이다. */
    private static final double CENTER_LAT = 37.7519;
    private static final double CENTER_LNG = 128.8761;

    private static final String WALK_REQUEST = """
            { "latitude": 37.7519, "longitude": 128.8761, "budget": 50000, "transport": "walk" }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private PlaceMediaRepository placeMediaRepository;
    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private SavedSlotRepository savedSlotRepository;

    private User user;
    private String token;
    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        user = userRepository.save(User.create("a@test.com", "테스터", null));
        token = "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private Place placeAt(String name, double latitude, double longitude) {
        return placeRepository.save(Place.builder()
                .contentId("it-" + (++sequence))
                .contentTypeId("12")
                .name(name)
                .category(TravelCategory.BEACH)
                .address("강원특별자치도 강릉시")
                .latitude(latitude)
                .longitude(longitude)
                .build());
    }

    // ---------- 슬롯 돌리기 ----------

    @Test
    @DisplayName("슬롯을 돌리면 장소와 미션이 공통 포맷으로 나간다")
    void drawReturnsPlaceAndMission() throws Exception {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        mockMvc.perform(post(DRAW_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WALK_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.slotId").isNumber())
                .andExpect(jsonPath("$.data.place.name").value("사천진해변"))
                .andExpect(jsonPath("$.data.place.category").value("해변"))
                .andExpect(jsonPath("$.data.place.distanceKm").isNumber())
                .andExpect(jsonPath("$.data.mission.missionId").isNumber())
                .andExpect(jsonPath("$.data.mission.title").isNotEmpty());
    }

    @Test
    @DisplayName("대표 이미지가 있으면 thumbnailUrl로 나간다")
    void drawExposesThumbnail() throws Exception {
        Place place = placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);
        placeMediaRepository.save(PlaceMedia.create(
                place, "https://cdn.example.com/beach.jpg", com.lottotrip.common.enums.MediaType.IMAGE));

        mockMvc.perform(post(DRAW_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WALK_REQUEST))
                .andExpect(jsonPath("$.data.place.thumbnailUrl")
                        .value("https://cdn.example.com/beach.jpg"));
    }

    @Test
    @DisplayName("반경 안에 후보가 없으면 404 SLOT_001")
    void drawFailsWithNoPlaceFound() throws Exception {
        // 강원 밖 좌표로 돌리면 실제로 이 상황이 된다(적재가 강원 한정).
        placeAt("멀리 있는 곳", CENTER_LAT + 1.0, CENTER_LNG + 1.0);

        mockMvc.perform(post(DRAW_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WALK_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("SLOT_001"));

        assertThat(savedSlotRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void drawRequiresAuthentication() throws Exception {
        mockMvc.perform(post(DRAW_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WALK_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("좌표가 빠지면 400 — 서비스에 닿기 전에 걸린다")
    void drawRejectsMissingCoordinate() throws Exception {
        mockMvc.perform(post(DRAW_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "budget": 50000, "transport": "walk" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("정의되지 않은 이동수단은 400")
    void drawRejectsUnknownTransport() throws Exception {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        mockMvc.perform(post(DRAW_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "latitude": 37.7519, "longitude": 128.8761, "budget": 50000, "transport": "bike" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    // ---------- 세션 분기 ----------

    @Test
    @DisplayName("처음 돌리면 세션이 새로 생기고, 이어서 돌리면 그 세션을 다시 쓴다")
    void reusesSessionAcrossDraws() throws Exception {
        placeAt("A", CENTER_LAT + 0.01, CENTER_LNG);
        placeAt("B", CENTER_LAT + 0.02, CENTER_LNG);

        mockMvc.perform(post(DRAW_PATH).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(WALK_REQUEST));
        assertThat(tripSessionRepository.findAll()).hasSize(1);   // 신규 생성 분기

        mockMvc.perform(post(DRAW_PATH).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(WALK_REQUEST));

        assertThat(tripSessionRepository.findAll()).hasSize(1);   // 재사용 분기
        assertThat(savedSlotRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("회원이 다르면 세션도 따로 만든다")
    void separatesSessionsPerUser() throws Exception {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        String otherToken = "Bearer " + jwtProvider.createAccessToken(other.getId());

        mockMvc.perform(post(DRAW_PATH).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(WALK_REQUEST));
        mockMvc.perform(post(DRAW_PATH).header("Authorization", otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(WALK_REQUEST));

        assertThat(tripSessionRepository.findAll()).hasSize(2);
    }

    // ---------- 결과 조회 ----------

    @Test
    @DisplayName("돌린 결과를 slotId로 다시 조회한다")
    void fetchesResultBySlotId() throws Exception {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);
        String body = mockMvc.perform(post(DRAW_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WALK_REQUEST))
                .andReturn().getResponse().getContentAsString();
        Long slotId = savedSlotRepository.findAll().get(0).getId();
        assertThat(body).contains("\"slotId\":" + slotId);

        mockMvc.perform(get(RESULT_PATH + slotId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotId").value(slotId))
                .andExpect(jsonPath("$.data.place.name").value("사천진해변"))
                // 인증키가 비어 있어 바깥 호출이 나가지 않는다. 그래도 우리 정보는 나간다.
                .andExpect(jsonPath("$.data.place.liveDetailLoaded").value(false));
    }

    @Test
    @DisplayName("없는 슬롯을 조회하면 404 SLOT_002")
    void resultFailsWhenMissing() throws Exception {
        mockMvc.perform(get(RESULT_PATH + "999999").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SLOT_002"));
    }

    @Test
    @DisplayName("남의 슬롯을 조회해도 404 SLOT_002 — 있다는 사실조차 알려주지 않는다")
    void resultHidesOtherUsersSlot() throws Exception {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        String otherToken = "Bearer " + jwtProvider.createAccessToken(other.getId());
        mockMvc.perform(post(DRAW_PATH).header("Authorization", otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(WALK_REQUEST));
        Long othersSlotId = savedSlotRepository.findAll().get(0).getId();

        mockMvc.perform(get(RESULT_PATH + othersSlotId).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SLOT_002"));
    }

    @Test
    @DisplayName("결과 조회도 토큰이 있어야 한다")
    void resultRequiresAuthentication() throws Exception {
        mockMvc.perform(get(RESULT_PATH + "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }
}
