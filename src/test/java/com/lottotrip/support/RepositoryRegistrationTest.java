package com.lottotrip.support;

import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.course.repository.CourseItemRepository;
import com.lottotrip.course.repository.TravelCourseRepository;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository 등록 및 기본 CRUD 검증. (roadmap 3-7, 3-8)
 *
 * Repository는 인터페이스만 선언하면 스프링이 구현체를 자동으로 만들어 준다.
 * 다만 패키지 위치가 스캔 범위를 벗어나면 **조용히 만들어지지 않는다.**
 * 그 상태는 실제로 쓰는 시점에야 드러나므로, 여기서 미리 전부 주입해 본다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryRegistrationTest extends PostgresContainerSupport {

    @Autowired private UserRepository userRepository;
    @Autowired private SocialAuthRepository socialAuthRepository;
    @Autowired private StateRepository stateRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceMediaRepository placeMediaRepository;
    @Autowired private TripSessionRepository tripSessionRepository;
    @Autowired private SavedSlotRepository savedSlotRepository;
    @Autowired private TravelCourseRepository travelCourseRepository;
    @Autowired private CourseItemRepository courseItemRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private UserMissionRepository userMissionRepository;

    @Test
    @DisplayName("모든 Repository가 빠짐없이 등록된다")
    void allRepositoriesAreRegistered() {
        List<JpaRepository<?, ?>> repositories = List.of(
                userRepository, socialAuthRepository,
                stateRepository, cityRepository, placeRepository, placeMediaRepository,
                tripSessionRepository, savedSlotRepository,
                travelCourseRepository, courseItemRepository,
                missionRepository, userMissionRepository);

        assertThat(repositories).hasSize(12).doesNotContainNull();
    }

    @Test
    @DisplayName("저장한 회원을 다시 찾을 수 있다")
    void savesAndFindsUser() {
        User saved = userRepository.save(User.create("test@khu.ac.kr", "주노", null));

        assertThat(userRepository.findById(saved.getId())).isPresent();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("저장한 장소를 다시 찾고 지울 수 있다")
    void savesFindsAndDeletesPlace() {
        State state = stateRepository.save(State.create("강원특별자치도"));
        City city = cityRepository.save(City.create(state, "강릉시"));
        Place saved = placeRepository.save(Place.builder()
                .contentId("3535323")
                .city(city)
                .name("사천진해변")
                .category(TravelCategory.NATURE_ATTRACTION)
                .address("강원 강릉시")
                .latitude(37.8021)
                .longitude(128.8954)
                .budgetTier(BudgetLevel.LOW)
                .publicTransportWeight(3)
                .build());

        assertThat(placeRepository.findById(saved.getId())).isPresent();

        placeRepository.delete(saved);

        assertThat(placeRepository.findById(saved.getId())).isEmpty();
    }
}
