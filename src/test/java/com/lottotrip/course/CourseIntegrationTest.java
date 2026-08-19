package com.lottotrip.course;

import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.course.repository.CourseItemRepository;
import com.lottotrip.course.repository.TravelCourseRepository;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 코스 API 통합 검증. (roadmap 7-5, tour_api_erd.md 4-4)
 *
 * 부품 동작은 `CourseServiceTest`가 이미 덮고 있으므로, 여기서는
 * **HTTP 경계에서만 드러나는 것**에 집중한다.
 *   - 인증이 실제로 걸리는가(401 `COMMON_401`)
 *   - 본문 검증이 서비스 이전에 도는가(400 `COMMON_400`)
 *   - 공통 응답 포맷(`success`·`data`·`error`)과 에러 코드가 명세대로 나가는가
 *   - 상태 코드가 맞는가 — 담기만 **201**이고 나머지는 200이다
 *
 * **바깥 호출이 없다.** 코스는 TourAPI를 부르지 않는다 — 담을 때 슬롯이 이미 가진
 * `place_id`만 쓰고, 조회도 우리 DB만 본다. 그래서 슬롯 쪽과 달리 막을 것이 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"tourapi.service-key=", "anthropic.api-key="})
@Transactional
class CourseIntegrationTest extends PostgresContainerSupport {

    private static final String ITEMS_PATH = "/api/v1/course/items";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private MissionRepository missionRepository;
    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private SavedSlotRepository savedSlotRepository;
    @Autowired
    private TravelCourseRepository travelCourseRepository;
    @Autowired
    private CourseItemRepository courseItemRepository;

    private User user;
    private String token;
    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        user = userRepository.save(User.create("a@test.com", "테스터", null));
        token = "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private Place placeNamed(String name) {
        return placeRepository.save(Place.builder()
                .contentId("it-" + (++sequence))
                .contentTypeId("12")
                .name(name)
                .category(TravelCategory.NATURE_ATTRACTION)
                .latitude(37.8021)
                .longitude(128.8954)
                .build());
    }

    /** 이 회원이 뽑아 둔 슬롯. 미션 없이 뽑힌 경우다. */
    private SavedSlot slotOf(User owner, Place place) {
        return slotOf(owner, place, null);
    }

    /** `presented`가 draw 때 제시한 미션이다. 코스 조회는 이 미션을 그대로 보여 준다(7-6). */
    private SavedSlot slotOf(User owner, Place place, Mission presented) {
        TripSession session = tripSessionRepository.save(TripSession.create(
                owner, BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761));
        return savedSlotRepository.save(SavedSlot.create(session, place, presented));
    }

    private String addBody(Long slotId) {
        return "{ \"slotId\": %d }".formatted(slotId);
    }

    // ---------- 담기 ----------

    @Test
    @DisplayName("코스에 담으면 201과 공통 포맷으로 나간다")
    void addReturnsCreated() throws Exception {
        Place place = placeNamed("사천진해변");
        SavedSlot slot = slotOf(user, place);

        mockMvc.perform(post(ITEMS_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(slot.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.itemId").isNumber())
                .andExpect(jsonPath("$.data.place.placeId").value(place.getId()))
                .andExpect(jsonPath("$.data.place.name").value("사천진해변"))
                .andExpect(jsonPath("$.data.addedAt").isNotEmpty());
    }

    @Test
    @DisplayName("같은 장소를 또 담으면 409 COURSE_001")
    void addFailsWhenAlreadyAdded() throws Exception {
        Place place = placeNamed("사천진해변");
        mockMvc.perform(post(ITEMS_PATH).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(addBody(slotOf(user, place).getId())));

        mockMvc.perform(post(ITEMS_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(slotOf(user, place).getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("COURSE_001"));

        assertThat(courseItemRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("없는 슬롯을 담으면 404 SLOT_002")
    void addFailsWhenSlotMissing() throws Exception {
        mockMvc.perform(post(ITEMS_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SLOT_002"));

        // 실패했으면 코스도 만들어지지 않아야 한다.
        assertThat(travelCourseRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("남의 슬롯을 담아도 404 SLOT_002 — 있다는 사실조차 알려주지 않는다")
    void addFailsWhenSlotBelongsToOther() throws Exception {
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        SavedSlot othersSlot = slotOf(other, placeNamed("남의 장소"));

        mockMvc.perform(post(ITEMS_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(othersSlot.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SLOT_002"));
    }

    @Test
    @DisplayName("slotId가 빠지면 400 — 서비스에 닿기 전에 걸린다")
    void addRejectsMissingSlotId() throws Exception {
        mockMvc.perform(post(ITEMS_PATH)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void addRequiresAuthentication() throws Exception {
        mockMvc.perform(post(ITEMS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    // ---------- 조회 ----------

    @Test
    @DisplayName("담은 순서대로 조회된다")
    void listsItemsInOrder() throws Exception {
        Place first = placeNamed("사천진해변");
        Mission presented = missionRepository.save(Mission.create(first, "해변 도착 인증하기", "설명", null, 100));
        mockMvc.perform(post(ITEMS_PATH).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(addBody(slotOf(user, first, presented).getId())));
        mockMvc.perform(post(ITEMS_PATH).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(addBody(slotOf(user, placeNamed("순포습지")).getId())));

        mockMvc.perform(get(ITEMS_PATH).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].place.name").value("사천진해변"))
                .andExpect(jsonPath("$.data.items[1].place.name").value("순포습지"))
                // draw 때 제시한 그 미션이 그대로 나온다 (7-6 — course_items.slot_id)
                .andExpect(jsonPath("$.data.items[0].mission.missionId").value(presented.getId()))
                // 아직 완료하지 않았으므로 false다. 완료하면 true가 되는 것은
                // CourseServiceTest와 FullScenarioIntegrationTest가 확인한다(9-1-1).
                .andExpect(jsonPath("$.data.items[0].mission.completed").value(false))
                // 슬롯이 미션 없이 뽑혔으면 mission이 null이다 — 그래도 목록에는 나온다
                .andExpect(jsonPath("$.data.items[1].mission").isEmpty());
    }

    @Test
    @DisplayName("한 번도 담지 않았으면 빈 목록이다 — 404가 아니다")
    void listsEmptyWhenNothingAdded() throws Exception {
        mockMvc.perform(get(ITEMS_PATH).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("조회도 토큰이 있어야 한다")
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ITEMS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    // ---------- 삭제 ----------

    @Test
    @DisplayName("담은 항목을 지운다")
    void removesItem() throws Exception {
        mockMvc.perform(post(ITEMS_PATH).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(addBody(slotOf(user, placeNamed("사천진해변")).getId())));
        Long itemId = courseItemRepository.findAll().get(0).getId();

        mockMvc.perform(delete(ITEMS_PATH + "/" + itemId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemId").value(itemId))
                .andExpect(jsonPath("$.data.deleted").value(true));

        assertThat(courseItemRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("없는 항목을 지우면 404 COURSE_002")
    void removeFailsWhenMissing() throws Exception {
        mockMvc.perform(delete(ITEMS_PATH + "/999999").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COURSE_002"));
    }

    @Test
    @DisplayName("남의 항목을 지워도 404 COURSE_002 — 남의 코스는 건드릴 수 없다")
    void removeFailsWhenItemBelongsToOther() throws Exception {
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        String otherToken = "Bearer " + jwtProvider.createAccessToken(other.getId());
        mockMvc.perform(post(ITEMS_PATH).header("Authorization", otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(addBody(slotOf(other, placeNamed("남의 장소")).getId())));
        Long othersItemId = courseItemRepository.findAll().get(0).getId();

        mockMvc.perform(delete(ITEMS_PATH + "/" + othersItemId).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_002"));

        assertThat(courseItemRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("삭제도 토큰이 있어야 한다")
    void removeRequiresAuthentication() throws Exception {
        mockMvc.perform(delete(ITEMS_PATH + "/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }
}
