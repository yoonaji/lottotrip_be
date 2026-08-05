package com.lottotrip.mission.entity;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.place.entity.City;
import com.lottotrip.common.enums.MediaType;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code missions} / {@code user_missions} 테이블 매핑 검증.
 * (tour_api_erd.md 1 — missions / user_missions)
 *
 * <p>{@code missions}는 장소마다 미리 등록해 두는 마스터 데이터이고,
 * {@code user_missions}는 회원이 실제로 수행한 기록이다. 완료 처리할 때 한 줄이 생긴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MissionEntityTest extends PostgresContainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    private User persistedUser() {
        User user = User.create("test@khu.ac.kr", "주노", null);
        entityManager.persist(user);
        return user;
    }

    private Place persistedPlace() {
        State state = State.create("강원특별자치도");
        entityManager.persist(state);
        City city = City.create(state, "강릉시");
        entityManager.persist(city);
        Place place = Place.create(city, "사천진해변", null, TravelCategory.BEACH,
                "강원 강릉시", 37.8021, 128.8954, BudgetLevel.LOW, 3);
        entityManager.persist(place);
        return place;
    }

    private Mission persistedMission() {
        Mission mission = Mission.create(persistedPlace(), "이 해변에서 일몰 사진 찍기",
                "해가 지는 방향을 등지고 찍으면 좋습니다", "https://cdn.example.com/guide.jpg", 100);
        entityManager.persist(mission);
        return mission;
    }

    @Test
    @DisplayName("미션을 저장하면 missionId가 자동으로 부여된다")
    void assignsMissionId() {
        Mission mission = Mission.create(persistedPlace(), "이 해변에서 일몰 사진 찍기",
                "안내 문구", "https://cdn.example.com/guide.jpg", 100);

        entityManager.persist(mission);
        entityManager.flush();

        assertThat(mission.getId()).isNotNull();
        assertThat(mission.getRewardPoint()).isEqualTo(100);
    }

    @Test
    @DisplayName("미션은 등록된 장소를 함께 조회할 수 있다")
    void missionBelongsToPlace() {
        Mission mission = persistedMission();
        entityManager.flush();
        entityManager.clear();

        Mission found = entityManager.find(Mission.class, mission.getId());
        assertThat(found.getPlace().getName()).isEqualTo("사천진해변");
    }

    @Test
    @DisplayName("한 장소에 여러 미션을 등록할 수 있다")
    void placeHasManyMissions() {
        // 슬롯 추첨 8단계: 해당 장소에 등록된 미션 중 랜덤 1개를 고른다. 후보가 여럿이어야 의미가 있다.
        Place place = persistedPlace();

        entityManager.persist(Mission.create(place, "일몰 사진 찍기", null, null, 100));
        entityManager.persist(Mission.create(place, "조개껍데기 줍기", null, null, 50));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }

    @Test
    @DisplayName("미션을 완료하면 기록에 완료 상태와 인증 시각이 남는다")
    void recordsCompletion() {
        UserMission record = UserMission.complete(persistedUser(), persistedMission());

        entityManager.persist(record);
        entityManager.flush();

        assertThat(record.getId()).isNotNull();
        assertThat(record.getStatus()).isEqualTo(MissionStatus.COMPLETED);
        assertThat(record.getCertifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("GPS로 인증하면 사진 없이도 완료 기록이 남는다")
    void allowsCompletionWithoutMedia() {
        // 미션 인증은 GPS 좌표 비교 방식으로 우선 구현한다. (tour_api_erd.md 미확정 항목)
        // 사진 인증을 전제로 media 컬럼을 NOT NULL로 잡으면 GPS 인증 자체가 막힌다.
        UserMission record = UserMission.complete(persistedUser(), persistedMission());

        entityManager.persist(record);
        entityManager.flush();
        entityManager.clear();

        UserMission found = entityManager.find(UserMission.class, record.getId());
        assertThat(found.getCertifiedMediaUrl()).isNull();
        assertThat(found.getMediaType()).isNull();
    }

    @Test
    @DisplayName("사진으로 인증하면 사진 주소와 종류가 함께 저장된다")
    void recordsCompletionWithMedia() {
        UserMission record = UserMission.completeWithMedia(persistedUser(), persistedMission(),
                "https://cdn.example.com/proof.jpg", MediaType.IMAGE);

        entityManager.persist(record);
        entityManager.flush();
        entityManager.clear();

        UserMission found = entityManager.find(UserMission.class, record.getId());
        assertThat(found.getCertifiedMediaUrl()).isEqualTo("https://cdn.example.com/proof.jpg");
        assertThat(found.getMediaType()).isEqualTo(MediaType.IMAGE);
    }

    @Test
    @DisplayName("완료 기록은 회원과 미션을 함께 조회할 수 있다")
    void recordLinksUserAndMission() {
        User user = persistedUser();
        Mission mission = persistedMission();
        UserMission record = UserMission.complete(user, mission);
        entityManager.persist(record);
        entityManager.flush();
        entityManager.clear();

        UserMission found = entityManager.find(UserMission.class, record.getId());
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getMission().getTitle()).isEqualTo("이 해변에서 일몰 사진 찍기");
    }

    @Test
    @DisplayName("status는 이름 문자열로 저장된다")
    void storesStatusAsName() {
        UserMission record = UserMission.complete(persistedUser(), persistedMission());
        entityManager.persist(record);
        entityManager.flush();

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("SELECT status FROM user_missions WHERE user_mission_id = :id")
                .setParameter("id", record.getId())
                .getSingleResult();

        assertThat(stored).hasToString("COMPLETED");
    }

    @Test
    @DisplayName("같은 회원이 같은 미션을 두 번 완료할 수 없다")
    void rejectsDuplicateCompletion() {
        // 명세의 409 ALREADY_COMPLETED를 DB 차원에서도 보장한다.
        // 조회 후 저장만으로는 같은 요청이 동시에 들어올 때 둘 다 통과한다.
        User user = persistedUser();
        Mission mission = persistedMission();
        entityManager.persist(UserMission.complete(user, mission));

        assertThatThrownBy(() -> entityManager.persist(UserMission.complete(user, mission)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_user_missions_user_mission");
    }

    @Test
    @DisplayName("회원이 다르면 같은 미션을 각자 완료할 수 있다")
    void allowsSameMissionForDifferentUsers() {
        Mission mission = persistedMission();

        entityManager.persist(UserMission.complete(persistedUser(), mission));
        entityManager.persist(UserMission.complete(persistedUser(), mission));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }
}
