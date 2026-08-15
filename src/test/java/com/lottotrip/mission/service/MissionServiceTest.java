package com.lottotrip.mission.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.dto.MissionCompleteRequest;
import com.lottotrip.mission.dto.MissionCompleteResponse;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.entity.MissionStatus;
import com.lottotrip.mission.entity.UserMission;
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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 미션 완료 처리 검증. (roadmap 8-2, tour_api_erd.md 4-5)
 *
 * <p><b>요청은 좌표만 준다.</b> "완료했다"를 그대로 믿지 않고 서버가 위치를 확인한다(8-1).
 * 믿어 버리면 집에서도 모든 미션을 완료할 수 있다.
 *
 * <p>DB는 진짜를 쓴다. {@code (user_id, mission_id)} UNIQUE 제약이 실재해야
 * 중복 완료 방지(8-3)를 이어서 증명할 수 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MissionServiceTest extends PostgresContainerSupport {

    /** 사천진해변. 미션이 붙는 장소의 좌표다. */
    private static final double PLACE_LAT = 37.8021;
    private static final double PLACE_LNG = 128.8954;

    /** 남북으로 1km 떨어지는 데 필요한 위도차(도). 8-1 테스트와 같은 환산이다. */
    private static final double DEGREES_PER_KM = 180.0 / (Math.PI * 6371.0);

    @Autowired
    private MissionRepository missionRepository;
    @Autowired
    private UserMissionRepository userMissionRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private UserRepository userRepository;

    private MissionService missionService;
    private User user;
    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        missionService = new MissionService(missionRepository, userMissionRepository,
                userRepository, new MissionLocationVerifier());

        user = userRepository.save(User.create("a@test.com", "테스터", null));
    }

    private Place placeNamed(String name) {
        return placeRepository.save(Place.builder()
                .contentId("c-" + (++sequence))
                .contentTypeId("12")
                .name(name)
                .category(TravelCategory.NATURE_ATTRACTION)
                .latitude(PLACE_LAT)
                .longitude(PLACE_LNG)
                .build());
    }

    private Mission missionAtBeach() {
        return missionRepository.save(
                Mission.create(placeNamed("사천진해변"), "사천진해변에 도착해 인증하기", "설명", null, 100));
    }

    /** 장소에서 정북으로 {@code km}만큼 떨어진 지점을 찍은 요청. */
    private MissionCompleteRequest requestFrom(double km) {
        return new MissionCompleteRequest(PLACE_LAT + km * DEGREES_PER_KM, PLACE_LNG);
    }

    // ---------- 완료 처리 (8-2) ----------

    @Test
    @DisplayName("장소에 도착해 있으면 미션이 완료된다")
    void completesMissionAtPlace() {
        Mission mission = missionAtBeach();

        MissionCompleteResponse response = missionService.complete(
                user.getId(), mission.getId(), requestFrom(0));

        assertThat(response.missionId()).isEqualTo(mission.getId());
        assertThat(response.completed()).isTrue();
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("완료하면 user_missions에 기록이 남는다")
    void persistsUserMission() {
        // 기록이 남아야 두 가지가 가능해진다: 중복 완료 판정(8-3)과 코스의 completed 표시.
        Mission mission = missionAtBeach();

        missionService.complete(user.getId(), mission.getId(), requestFrom(0));

        assertThat(userMissionRepository.findAll())
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getUser().getId()).isEqualTo(user.getId());
                    assertThat(record.getMission().getId()).isEqualTo(mission.getId());
                    assertThat(record.getStatus()).isEqualTo(MissionStatus.COMPLETED);
                    assertThat(record.getCertifiedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("GPS 인증이라 사진 정보는 비어 있다")
    void leavesMediaEmpty() {
        // 사진 인증은 아직 도입하지 않았다(미확정 항목 8-1). 컬럼이 nullable이라
        // 나중에 붙여도 스키마는 그대로 쓸 수 있다.
        Mission mission = missionAtBeach();

        missionService.complete(user.getId(), mission.getId(), requestFrom(0));

        UserMission record = userMissionRepository.findAll().get(0);
        assertThat(record.getCertifiedMediaUrl()).isNull();
        assertThat(record.getMediaType()).isNull();
    }

    @Test
    @DisplayName("반경 안(400m)이면 완료된다")
    void completesInsideRadius() {
        Mission mission = missionAtBeach();

        assertThat(missionService.complete(user.getId(), mission.getId(), requestFrom(0.4)).completed())
                .isTrue();
    }

    // ---------- 완료할 수 없는 경우 ----------

    @Test
    @DisplayName("없는 미션이면 MISSION_NOT_FOUND")
    void failsWhenMissionMissing() {
        assertThatThrownBy(() -> missionService.complete(user.getId(), 999_999L, requestFrom(0)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MISSION_NOT_FOUND);
    }

    @Test
    @DisplayName("반경 밖(600m)이면 VERIFICATION_FAILED")
    void failsWhenTooFar() {
        Mission mission = missionAtBeach();

        assertThatThrownBy(() -> missionService.complete(user.getId(), mission.getId(), requestFrom(0.6)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("인증에 실패하면 기록을 남기지 않는다")
    void doesNotPersistWhenVerificationFails() {
        // 실패한 시도까지 저장하면 (user_id, mission_id) UNIQUE에 걸려
        // 나중에 진짜 도착했을 때 완료할 수 없게 된다.
        Mission mission = missionAtBeach();

        assertThatThrownBy(() -> missionService.complete(user.getId(), mission.getId(), requestFrom(5)))
                .isInstanceOf(CustomException.class);

        assertThat(userMissionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 UNAUTHORIZED")
    void failsWhenUserMissing() {
        // 토큰이 유효해도 그 회원이 아직 있는지는 별개다(탈퇴). 코스(7-1)와 같은 원칙이다.
        Mission mission = missionAtBeach();

        assertThatThrownBy(() -> missionService.complete(999_999L, mission.getId(), requestFrom(0)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("다른 회원이 같은 미션을 완료해도 서로 막지 않는다")
    void allowsDifferentUsersOnSameMission() {
        // 미션은 장소에 붙은 공용 자산이다. 한 사람이 완료했다고 다른 사람이 못 하면 안 된다.
        Mission mission = missionAtBeach();
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));

        missionService.complete(user.getId(), mission.getId(), requestFrom(0));
        missionService.complete(other.getId(), mission.getId(), requestFrom(0));

        assertThat(userMissionRepository.findAll()).hasSize(2);
    }
}
