package com.lottotrip.mission;

import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미션 API 통합 검증. (roadmap 8-4, tour_api_erd.md 4-5)
 *
 * <p>판정 로직 자체는 {@code MissionLocationVerifierTest}(8-1)와 {@code MissionServiceTest}(8-2·8-3)가
 * 이미 덮고 있다. 여기서는 <b>HTTP 경계에서만 드러나는 것</b>을 본다.
 * <ul>
 *   <li>인증이 실제로 걸리는가(401 {@code COMMON_401})</li>
 *   <li>본문 검증이 서비스 이전에 도는가(400 {@code COMMON_400}) — 좌표가 빠지거나 범위를 벗어난 경우</li>
 *   <li>명세의 에러 4종이 그 상태 코드·코드값으로 나가는가
 *       ({@code MISSION_001} 404 / {@code MISSION_002} 409 / {@code MISSION_003} 422)</li>
 *   <li>성공이 <b>200</b>인가 — 코스 담기(201)와 다르다</li>
 * </ul>
 *
 * <p><b>바깥 호출이 없다.</b> 미션 완료는 좌표를 받아 우리 DB의 장소와 거리만 잰다.
 * TourAPI도 Claude API도 부르지 않으므로 막을 것이 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"tourapi.service-key=", "anthropic.api-key="})
@Transactional
class MissionIntegrationTest extends PostgresContainerSupport {

    /** 사천진해변. */
    private static final double PLACE_LAT = 37.8021;
    private static final double PLACE_LNG = 128.8954;

    /** 남북으로 1km 떨어지는 데 필요한 위도차(도). 8-1·8-2 테스트와 같은 환산이다. */
    private static final double DEGREES_PER_KM = 180.0 / (Math.PI * 6371.0);

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
    private UserMissionRepository userMissionRepository;

    private User user;
    private String token;
    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        user = userRepository.save(User.create("a@test.com", "테스터", null));
        token = "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private Mission missionAtBeach() {
        Place place = placeRepository.save(Place.builder()
                .contentId("mi-" + (++sequence))
                .contentTypeId("12")
                .name("사천진해변")
                .category(TravelCategory.NATURE_ATTRACTION)
                .latitude(PLACE_LAT)
                .longitude(PLACE_LNG)
                .build());
        return missionRepository.save(
                Mission.create(place, "사천진해변에 도착해 인증하기", "설명", null, 100));
    }

    private String completePath(Long missionId) {
        return "/api/v1/missions/" + missionId + "/complete";
    }

    /** 장소에서 정북으로 {@code km}만큼 떨어진 지점을 찍은 요청 본문. */
    private String bodyFrom(double km) {
        return "{ \"latitude\": %s, \"longitude\": %s }"
                .formatted(PLACE_LAT + km * DEGREES_PER_KM, PLACE_LNG);
    }

    /** 완료 요청 한 번. 본문·헤더가 매번 같아 묶었다. */
    private org.springframework.test.web.servlet.ResultActions complete(Long missionId, String body) throws Exception {
        return mockMvc.perform(post(completePath(missionId))
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("장소에 도착해 있으면 200과 공통 포맷으로 나간다")
    void completeReturnsOk() throws Exception {
        Mission mission = missionAtBeach();

        complete(mission.getId(), bodyFrom(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.missionId").value(mission.getId()))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        assertThat(userMissionRepository.existsByUserIdAndMissionId(user.getId(), mission.getId())).isTrue();
    }

    // ---------- 명세의 에러 4종 ----------

    @Test
    @DisplayName("없는 미션이면 404 MISSION_001")
    void failsWhenMissionMissing() throws Exception {
        complete(999_999L, bodyFrom(0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("MISSION_001"));
    }

    @Test
    @DisplayName("이미 완료한 미션이면 409 MISSION_002")
    void failsWhenAlreadyCompleted() throws Exception {
        Mission mission = missionAtBeach();
        complete(mission.getId(), bodyFrom(0)).andExpect(status().isOk());

        complete(mission.getId(), bodyFrom(0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MISSION_002"));

        // 두 번째 요청이 기록을 덧붙이지 않았는지 확인한다.
        assertThat(userMissionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("반경 밖(600m)이면 422 MISSION_003")
    void failsWhenTooFar() throws Exception {
        Mission mission = missionAtBeach();

        complete(mission.getId(), bodyFrom(0.6))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MISSION_003"));

        // 실패한 시도는 기록으로 남지 않는다 — 남으면 나중에 진짜 도착해도 완료할 수 없다.
        assertThat(userMissionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("토큰이 없으면 401 COMMON_401")
    void requiresAuthentication() throws Exception {
        Mission mission = missionAtBeach();

        mockMvc.perform(post(completePath(mission.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyFrom(0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        assertThat(userMissionRepository.findAll()).isEmpty();
    }

    // ---------- 본문 검증 (서비스에 닿기 전) ----------

    @Test
    @DisplayName("좌표가 빠지면 400 — 0.0으로 취급되지 않는다")
    void rejectsMissingCoordinates() throws Exception {
        Mission mission = missionAtBeach();

        complete(mission.getId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("범위를 벗어난 좌표는 400 — 위도 91도 같은 값은 존재하지 않는다")
    void rejectsOutOfRangeCoordinates() throws Exception {
        Mission mission = missionAtBeach();

        complete(mission.getId(), "{ \"latitude\": 91.0, \"longitude\": 128.8954 }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    // ---------- 미션은 공용 자산이다 ----------

    @Test
    @DisplayName("다른 회원은 같은 미션을 다시 완료할 수 있다")
    void allowsAnotherUserOnSameMission() throws Exception {
        Mission mission = missionAtBeach();
        complete(mission.getId(), bodyFrom(0)).andExpect(status().isOk());

        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        mockMvc.perform(post(completePath(mission.getId()))
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(other.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyFrom(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        assertThat(userMissionRepository.findAll()).hasSize(2);
    }
}
