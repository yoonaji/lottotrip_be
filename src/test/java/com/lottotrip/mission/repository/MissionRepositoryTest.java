package com.lottotrip.mission.repository;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.entity.UserMission;
import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 조회 검증. (tour_api_erd.md 2-4 step 8, 4-5)
 *
 * <p>슬롯이 장소를 뽑으면 그 장소의 미션 중 하나를 골라야 하고,
 * 완료 처리할 때는 이미 완료했는지 확인해야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MissionRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private UserMissionRepository userMissionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistedUser() {
        User user = User.create("test@khu.ac.kr", "주노", null);
        entityManager.persist(user);
        return user;
    }

    /** 한 테스트에서 장소를 여러 개 만들 수 있다. content_id는 UNIQUE라 매번 다른 값이어야 한다. */
    private int contentSeq = 0;

    private Place persistedPlace(String name) {
        State state = State.create("강원특별자치도");
        entityManager.persist(state);
        City city = City.create(state, "강릉시");
        entityManager.persist(city);
        Place place = Place.builder()
                .contentId("TEST-" + (++contentSeq))
                .city(city)
                .name(name)
                .category(TravelCategory.BEACH)
                .address("강원 강릉시")
                .latitude(37.8021)
                .longitude(128.8954)
                .budgetTier(BudgetLevel.LOW)
                .publicTransportWeight(3)
                .build();
        entityManager.persist(place);
        return place;
    }

    @Test
    @DisplayName("장소에 등록된 미션을 모두 찾는다")
    void findsMissionsByPlace() {
        // 슬롯 추첨 8단계: 해당 place_id에 등록된 미션 중 랜덤 1개를 고른다.
        Place place = persistedPlace("사천진해변");
        entityManager.persist(Mission.create(place, "일몰 사진 찍기", null, null, 100));
        entityManager.persist(Mission.create(place, "조개껍데기 줍기", null, null, 50));
        entityManager.flush();

        var missions = missionRepository.findByPlaceId(place.getId());

        assertThat(missions).hasSize(2);
    }

    @Test
    @DisplayName("다른 장소의 미션은 섞이지 않는다")
    void doesNotMixMissionsFromOtherPlaces() {
        Place beach = persistedPlace("사천진해변");
        Place port = persistedPlace("주문진항");
        entityManager.persist(Mission.create(beach, "일몰 사진 찍기", null, null, 100));
        entityManager.persist(Mission.create(port, "오징어 먹기", null, null, 100));
        entityManager.flush();

        var missions = missionRepository.findByPlaceId(beach.getId());

        assertThat(missions).hasSize(1);
        assertThat(missions.get(0).getTitle()).isEqualTo("일몰 사진 찍기");
    }

    @Test
    @DisplayName("미션이 없는 장소는 빈 목록을 준다")
    void returnsEmptyWhenNoMission() {
        var missions = missionRepository.findByPlaceId(persistedPlace("사천진해변").getId());

        assertThat(missions).isEmpty();
    }

    @Test
    @DisplayName("이미 완료한 미션인지 확인한다")
    void checksAlreadyCompleted() {
        // 명세의 409 ALREADY_COMPLETED 판정에 쓴다. (tour_api_erd.md 4-5)
        User user = persistedUser();
        Place place = persistedPlace("사천진해변");
        Mission done = Mission.create(place, "일몰 사진 찍기", null, null, 100);
        Mission notDone = Mission.create(place, "조개껍데기 줍기", null, null, 50);
        entityManager.persist(done);
        entityManager.persist(notDone);
        entityManager.persist(UserMission.complete(user, done));
        entityManager.flush();

        assertThat(userMissionRepository.existsByUserIdAndMissionId(user.getId(), done.getId())).isTrue();
        assertThat(userMissionRepository.existsByUserIdAndMissionId(user.getId(), notDone.getId())).isFalse();
    }

    @Test
    @DisplayName("다른 회원이 완료했어도 나는 완료하지 않은 것이다")
    void completionIsPerUser() {
        Place place = persistedPlace("사천진해변");
        Mission mission = Mission.create(place, "일몰 사진 찍기", null, null, 100);
        entityManager.persist(mission);
        entityManager.persist(UserMission.complete(persistedUser(), mission));
        User me = persistedUser();
        entityManager.flush();

        assertThat(userMissionRepository.existsByUserIdAndMissionId(me.getId(), mission.getId())).isFalse();
    }
}
