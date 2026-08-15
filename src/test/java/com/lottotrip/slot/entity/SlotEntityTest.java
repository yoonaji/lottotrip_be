package com.lottotrip.slot.entity;

import com.lottotrip.common.enums.BudgetLevel;
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
 * {@code trip_sessions} / {@code saved_slots} 테이블 매핑 검증.
 * (tour_api_erd.md 1 — trip_sessions / saved_slots)
 *
 * <p>세션은 프론트가 만들지 않는다. 서버가 회원 기준으로 12시간 이내 세션을 찾아 재사용하고,
 * 없으면 새로 만든다(find-or-create). 그 판정 로직은 6-1에서 붙인다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SlotEntityTest extends PostgresContainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    private User persistedUser() {
        User user = User.create("test@khu.ac.kr", "주노", null);
        entityManager.persist(user);
        return user;
    }

    /** 한 테스트에서 장소를 여러 개 만들 수 있다. content_id는 UNIQUE라 매번 다른 값이어야 한다. */
    private int contentSeq = 0;

    private String nextContentId() {
        return "TEST-" + (++contentSeq);
    }

    private Place persistedPlace(String name) {
        State state = State.create("강원특별자치도");
        entityManager.persist(state);
        City city = City.create(state, "강릉시");
        entityManager.persist(city);
        Place place = Place.builder()
                .contentId(nextContentId())
                .city(city)
                .name(name)
                .category(TravelCategory.NATURE_ATTRACTION)
                .address("강원 강릉시")
                .latitude(37.8021)
                .longitude(128.8954)
                .budgetTier(BudgetLevel.LOW)
                .publicTransportWeight(3)
                .build();
        entityManager.persist(place);
        return place;
    }

    private TripSession sampleSession() {
        return TripSession.create(persistedUser(), BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761);
    }

    @Test
    @DisplayName("세션을 저장하면 sessionId와 createdAt이 자동으로 채워진다")
    void assignsSessionIdAndCreatedAt() {
        TripSession session = sampleSession();

        entityManager.persist(session);
        entityManager.flush();

        assertThat(session.getId()).isNotNull();
        // createdAt은 12시간 유효성 판단의 기준이다. 비어 있으면 세션 재사용 판단 자체가 불가능하다.
        assertThat(session.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("검색 반경은 요청이 아니라 이동수단에서 정해진다")
    void derivesRadiusFromTransport() {
        // 결정 2: 반경은 요청 파라미터가 아니다. 세션을 만들 때 이동수단으로부터 채워진다.
        TripSession walking = TripSession.create(persistedUser(), BudgetLevel.LOW, TransportType.WALK, 37.0, 128.0);
        TripSession driving = TripSession.create(persistedUser(), BudgetLevel.LOW, TransportType.CAR, 37.0, 128.0);

        assertThat(walking.getSearchRadiusKm()).isEqualTo(10);
        assertThat(driving.getSearchRadiusKm()).isEqualTo(30);
    }

    @Test
    @DisplayName("숙소 좌표가 소수점까지 그대로 저장되고 다시 조회된다")
    void persistsAccommodationCoordinate() {
        TripSession session = sampleSession();
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        TripSession found = entityManager.find(TripSession.class, session.getId());
        assertThat(found.getAccommodationLatitude()).isEqualTo(37.7519);
        assertThat(found.getAccommodationLongitude()).isEqualTo(128.8761);
    }

    @Test
    @DisplayName("세션은 소유한 회원을 함께 조회할 수 있다")
    void sessionBelongsToUser() {
        User user = persistedUser();
        TripSession session = TripSession.create(user, BudgetLevel.HIGH, TransportType.CAR, 37.0, 128.0);
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        TripSession found = entityManager.find(TripSession.class, session.getId());
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("budgetRange와 transportation은 이름 문자열로 저장된다")
    void storesEnumsAsName() {
        TripSession session = sampleSession();
        entityManager.persist(session);
        entityManager.flush();

        Object[] stored = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("SELECT budget_range, transportation FROM trip_sessions WHERE session_id = :id")
                .setParameter("id", session.getId())
                .getSingleResult();

        assertThat(stored[0]).hasToString("MEDIUM");
        assertThat(stored[1]).hasToString("WALK");
    }

    @Test
    @DisplayName("슬롯 결과를 저장하면 slotId와 createdAt이 자동으로 채워진다")
    void assignsSlotIdAndCreatedAt() {
        TripSession session = sampleSession();
        entityManager.persist(session);
        SavedSlot slot = SavedSlot.create(session, persistedPlace("사천진해변"), null);

        entityManager.persist(slot);
        entityManager.flush();

        // slotId는 API 응답의 slotId이자 코스 추가 시 참조하는 값이다. (결정 3)
        assertThat(slot.getId()).isNotNull();
        assertThat(slot.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("슬롯 결과는 세션과 장소를 함께 조회할 수 있다")
    void slotLinksSessionAndPlace() {
        TripSession session = sampleSession();
        entityManager.persist(session);
        SavedSlot slot = SavedSlot.create(session, persistedPlace("사천진해변"), null);
        entityManager.persist(slot);
        entityManager.flush();
        entityManager.clear();

        SavedSlot found = entityManager.find(SavedSlot.class, slot.getId());
        assertThat(found.getSession().getId()).isEqualTo(session.getId());
        assertThat(found.getPlace().getName()).isEqualTo("사천진해변");
    }

    @Test
    @DisplayName("같은 세션에서 같은 장소가 다시 뽑혀도 저장된다")
    void allowsSamePlaceDrawnAgain() {
        // 슬롯은 랜덤 추첨이라 같은 장소가 또 나올 수 있다. 코스와 달리 중복을 막지 않는다.
        TripSession session = sampleSession();
        entityManager.persist(session);
        Place place = persistedPlace("사천진해변");

        entityManager.persist(SavedSlot.create(session, place, null));
        entityManager.persist(SavedSlot.create(session, place, null));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }
}
