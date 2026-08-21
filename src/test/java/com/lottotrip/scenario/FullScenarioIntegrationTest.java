package com.lottotrip.scenario;

import com.jayway.jsonpath.JsonPath;
import com.lottotrip.course.repository.CourseItemRepository;
import com.lottotrip.course.repository.TravelCourseRepository;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.service.RealtimePlaceFinder;
import com.lottotrip.place.tourapi.TourApiPlaceItem;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.support.StubbedSocialServerConfig;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전체 시나리오 통합 검증. (roadmap 9-1)
 *
 * **로그인 → 슬롯 돌리기 → 코스 담기 → 미션 완료**를 한 번에 이어서 지난다.
 * 도메인별 통합 테스트(4-8·6-8·7-5·8-4)는 각자 자기 API만 보므로,
 * **도메인 사이를 건너갈 때만 드러나는 것**은 여기서만 확인된다.
 *   - 로그인으로 받은 **진짜 토큰**이 슬롯·코스·미션 API에서 통하는가
 *       (다른 테스트들은 `JwtProvider`로 토큰을 직접 만들어 쓴다)
 *   - draw가 만든 `slotId`를 코스가 그대로 받아 쓰는가
 *   - draw가 제시한 **그 미션**이 코스 조회에 다시 나오는가 (7-6 · 결정 14)
 *   - 그 미션 번호로 완료 처리가 되는가 — 즉 세 도메인이 같은 식별자를 주고받는가
 *
 * **바깥 호출은 두 군데를 막는다.**
 *   - **카카오** — {@link StubbedSocialServerConfig}로 통로만 가짜로 바꾼다.
 *       로그인 로직 자체는 진짜가 돈다
 *   - **TourAPI 추첨** — `RealtimePlaceFinder`를 목으로 바꾼다.
 *       막지 않으면 테스트가 돌 때마다 일일 할당량을 깎는다
 * Claude(미션 문구 생성)는 `anthropic.api-key`를 비워 템플릿 생성기로 내려가게 한다.
 * 나머지 — 시큐리티 필터, 컨트롤러, 서비스, JPA, PostgreSQL — 는 전부 진짜다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubbedSocialServerConfig.class)
@TestPropertySource(properties = {"tourapi.service-key=", "anthropic.api-key=",
        "oauth.kakao.app-id=" + StubbedSocialServerConfig.KAKAO_APP_ID})
@Transactional
class FullScenarioIntegrationTest extends PostgresContainerSupport {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String DRAW_PATH = "/api/v1/slot/draw";
    private static final String ITEMS_PATH = "/api/v1/course/items";

    /** 뽑히는 장소(사천진해변)의 좌표. 미션 완료 요청도 이 자리에서 보낸다. */
    private static final String PLACE_LAT = "37.8021";
    private static final String PLACE_LNG = "128.8954";

    private static final String KAKAO_RESPONSE = """
            {
              "id": 987654321,
              "kakao_account": {
                "email": "potato@example.com",
                "profile": { "nickname": "감자러버", "profile_image_url": "https://img.kakao.com/p.png" }
              }
            }
            """;

    /** 강릉시청 부근에서 걸어서 갈 만한 곳을 찾는 요청. */
    private static final String DRAW_REQUEST = """
            { "latitude": 37.7519, "longitude": 128.8761, "budget": 50000, "transport": "walk" }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MockRestServiceServer kakaoServer;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private SavedSlotRepository savedSlotRepository;
    @Autowired
    private TravelCourseRepository travelCourseRepository;
    @Autowired
    private CourseItemRepository courseItemRepository;
    @Autowired
    private UserMissionRepository userMissionRepository;

    /** 추첨만 가짜다. 저장·미션 생성·응답 조립은 진짜가 돈다. */
    @MockitoBean
    private RealtimePlaceFinder realtimePlaceFinder;

    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        kakaoServer.reset();
        StubbedSocialServerConfig.expectValidTokenInfo(kakaoServer);
    }

    // ---------- 각 단계를 한 줄로 부르기 위한 도우미 ----------

    /** 이 이름의 장소가 뽑히도록 해 둔다. 좌표는 {@link #PLACE_LAT}/{@link #PLACE_LNG}다. */
    private void givenDrawn(String name) {
        TourApiPlaceItem item = new TourApiPlaceItem(
                "sc-" + (++sequence), "12", name, "강원특별자치도 강릉시", "",
                "32", "1", "A01", "A0101", "A01011200",
                "https://cdn.example.com/beach.jpg", "", PLACE_LNG, PLACE_LAT, "1113.0", "20240115103045");
        given(realtimePlaceFinder.drawOne(any(), anyDouble(), anyDouble(), anyInt(), any()))
                .willReturn(Optional.of(item));
    }

    /** 카카오 로그인 → 액세스 토큰. 실제 로그인 API를 지나 발급된 진짜 토큰이다. */
    private String login() throws Exception {
        kakaoServer.expect(ExpectedCount.manyTimes(),
                        requestTo(StubbedSocialServerConfig.KAKAO_USER_INFO_URI))
                .andRespond(withSuccess(KAKAO_RESPONSE, MediaType.APPLICATION_JSON));

        MvcResult result = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "kakao", "providerToken": "valid-kakao-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.isNewUser").value(true))
                .andReturn();

        return "Bearer " + read(result, "$.data.accessToken");
    }

    private MvcResult draw(String token) throws Exception {
        return mockMvc.perform(post(DRAW_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAW_REQUEST))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult addToCourse(String token, int slotId) throws Exception {
        return mockMvc.perform(post(ITEMS_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"slotId\": %d }".formatted(slotId)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** 장소에 서서 미션을 완료한다. */
    private void completeMission(String token, int missionId) throws Exception {
        mockMvc.perform(post("/api/v1/missions/" + missionId + "/complete")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"latitude\": %s, \"longitude\": %s }".formatted(PLACE_LAT, PLACE_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value(missionId))
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    // ---------- 본 시나리오 ----------

    @Test
    @DisplayName("로그인 → 슬롯 → 코스 담기 → 미션 완료가 한 번에 이어진다")
    void fullJourney() throws Exception {
        long usersBefore = userRepository.count();

        // 1. 로그인 — 여기서 받은 토큰 하나로 남은 전 구간을 지난다.
        String token = login();
        assertThat(userRepository.count()).isEqualTo(usersBefore + 1);

        // 2. 슬롯 돌리기 — 장소와 미션이 함께 나온다.
        givenDrawn("사천진해변");
        MvcResult drawn = draw(token);
        int slotId = read(drawn, "$.data.slotId");
        int missionId = read(drawn, "$.data.mission.missionId");
        assertThat((String) read(drawn, "$.data.place.name")).isEqualTo("사천진해변");

        // 세션·슬롯·장소가 실제로 저장됐다. 뽑기만 하고 안 남기면 코스에 담을 수 없다.
        assertThat(tripSessionRepository.findAll()).hasSize(1);
        assertThat(savedSlotRepository.findAll()).hasSize(1);
        assertThat(placeRepository.findAll()).hasSize(1);

        // 3. 코스 담기 — draw가 준 slotId를 그대로 쓴다.
        MvcResult added = addToCourse(token, slotId);
        int itemId = read(added, "$.data.itemId");

        assertThat(travelCourseRepository.findAll()).hasSize(1);
        assertThat(courseItemRepository.findAll()).hasSize(1);

        // 4. 코스 조회 — 담은 장소와 함께, draw 때 제시한 그 미션이 다시 나온다(7-6, 결정 14).
        mockMvc.perform(get(ITEMS_PATH).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].itemId").value(itemId))
                .andExpect(jsonPath("$.data.items[0].place.name").value("사천진해변"))
                .andExpect(jsonPath("$.data.items[0].mission.missionId").value(missionId));

        // 5. 미션 완료 — draw가 준 missionId를 그대로 쓴다.
        completeMission(token, missionId);

        assertThat(userMissionRepository.findAll())
                .singleElement()
                .satisfies(record -> assertThat(record.getMission().getId()).isEqualTo((long) missionId));
    }

    @Test
    @DisplayName("두 번 돌려도 세션은 하나다 — 12시간 안이면 재사용한다")
    void reusesTripSessionAcrossDraws() throws Exception {
        // 결정 1. 프론트가 sessionId를 보내지 않으므로, 서버가 회원 기준으로 찾아 재사용한다.
        // 세션이 매번 새로 생기면 "이번 여행"이라는 단위가 무의미해진다.
        String token = login();

        givenDrawn("사천진해변");
        draw(token);
        givenDrawn("순포습지");
        draw(token);

        assertThat(tripSessionRepository.findAll()).hasSize(1);
        assertThat(savedSlotRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("토큰을 갱신해도 슬롯을 이어서 돌릴 수 있다")
    void refreshedTokenKeepsWorking() throws Exception {
        // 액세스 토큰은 1시간이면 만료된다. 여행 도중 갱신했을 때 흐름이 끊기면 안 된다.
        // 4-8에도 비슷한 테스트가 있지만 그쪽은 갱신 토큰으로 로그아웃(같은 인증 도메인)까지만 본다.
        // 여기서는 갱신한 토큰이 다른 도메인(슬롯)에서도 통하는지를 확인한다.
        kakaoServer.expect(ExpectedCount.manyTimes(),
                        requestTo(StubbedSocialServerConfig.KAKAO_USER_INFO_URI))
                .andRespond(withSuccess(KAKAO_RESPONSE, MediaType.APPLICATION_JSON));

        MvcResult loggedIn = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "kakao", "providerToken": "valid-kakao-token"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult refreshed = mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}"
                                .formatted((String) read(loggedIn, "$.data.refreshToken"))))
                .andExpect(status().isOk())
                .andReturn();

        givenDrawn("사천진해변");
        draw("Bearer " + read(refreshed, "$.data.accessToken"));

        assertThat(savedSlotRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("다른 회원이 남의 슬롯을 담을 수 없다 — 로그인부터 이어서 확인한다")
    void cannotAddAnothersSlot() throws Exception {
        // 소유권 검사는 7-1이 이미 덮고 있지만, 그 테스트는 토큰을 직접 만들어 쓴다.
        // 실제 로그인으로 발급된 토큰에서도 같은 판정이 나오는지 여기서 확인한다.
        String owner = login();
        givenDrawn("사천진해변");
        int slotId = read(draw(owner), "$.data.slotId");

        kakaoServer.reset();
        StubbedSocialServerConfig.expectValidTokenInfo(kakaoServer);
        kakaoServer.expect(ExpectedCount.manyTimes(),
                        requestTo(StubbedSocialServerConfig.KAKAO_USER_INFO_URI))
                .andRespond(withSuccess("""
                        {
                          "id": 111222333,
                          "kakao_account": {
                            "email": "other@example.com",
                            "profile": { "nickname": "다른 사람" }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        MvcResult otherLogin = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "kakao", "providerToken": "another-kakao-token"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post(ITEMS_PATH)
                        .header("Authorization", "Bearer " + read(otherLogin, "$.data.accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"slotId\": %d }".formatted(slotId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SLOT_002"));

        assertThat(courseItemRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("미션을 완료하면 코스에서 completed=true로 보인다")
    void completedMissionShowsInCourse() throws Exception {
        // 도메인을 건너가는 판정이다 — 미션 도메인에 남은 기록을 코스 조회가 읽는다.
        // 판정 근거는 user_missions에 줄이 있는가뿐이고, 그 줄은 GPS 인증을 통과해야 생긴다(8-1·8-2).
        String token = login();
        givenDrawn("사천진해변");
        MvcResult drawn = draw(token);
        addToCourse(token, read(drawn, "$.data.slotId"));

        // 완료하기 전에는 false다.
        mockMvc.perform(get(ITEMS_PATH).header("Authorization", token))
                .andExpect(jsonPath("$.data.items[0].mission.completed").value(false));

        completeMission(token, read(drawn, "$.data.mission.missionId"));

        mockMvc.perform(get(ITEMS_PATH).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].mission.completed").value(true));

        assertThat(userMissionRepository.findAll()).hasSize(1);
    }
}
