package com.lottotrip.place.entity;

import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code states} / {@code cities} 테이블 매핑 검증. (tour_api_erd.md 1 — states / cities)
 *
 * <p>지역은 "강원도 → 강릉시"처럼 2단계로 나뉜다. 장소는 시·군에 속한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegionEntityTest extends PostgresContainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("광역 지역을 저장하면 stateId가 자동으로 부여된다")
    void assignsStateId() {
        State state = State.create("강원특별자치도");

        entityManager.persist(state);
        entityManager.flush();

        assertThat(state.getId()).isNotNull();
        assertThat(state.getStateName()).isEqualTo("강원특별자치도");
    }

    @Test
    @DisplayName("시·군은 자신이 속한 광역 지역을 함께 조회할 수 있다")
    void cityBelongsToState() {
        State state = State.create("강원특별자치도");
        entityManager.persist(state);
        City city = City.create(state, "강릉시");

        entityManager.persist(city);
        entityManager.flush();
        entityManager.clear();

        City found = entityManager.find(City.class, city.getId());
        assertThat(found.getCityName()).isEqualTo("강릉시");
        assertThat(found.getState().getStateName()).isEqualTo("강원특별자치도");
    }

    @Test
    @DisplayName("한 광역 지역에 여러 시·군이 속할 수 있다")
    void stateHasManyCities() {
        State state = State.create("강원특별자치도");
        entityManager.persist(state);

        entityManager.persist(City.create(state, "강릉시"));
        entityManager.persist(City.create(state, "속초시"));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }

    // ---------- TourAPI 지역코드 (5-8, 결정 10) ----------

    @Test
    @DisplayName("TourAPI 시도 코드가 저장되고 다시 조회된다")
    void persistsTourAreaCode() {
        // TourAPI는 장소의 지역을 코드로만 준다(이름은 주지 않는다).
        // 이 코드를 담아 두어야 적재할 때 어느 지역 행인지 이을 수 있다.
        State state = State.create("강원특별자치도", "32");

        entityManager.persist(state);
        entityManager.flush();
        entityManager.clear();

        State found = entityManager.find(State.class, state.getId());
        assertThat(found.getTourAreaCode()).isEqualTo("32");
        assertThat(found.getStateName()).isEqualTo("강원특별자치도");
    }

    @Test
    @DisplayName("TourAPI 시군구 코드가 저장되고 다시 조회된다")
    void persistsTourSigunguCode() {
        State state = State.create("강원특별자치도", "32");
        entityManager.persist(state);
        City city = City.create(state, "강릉시", "1");

        entityManager.persist(city);
        entityManager.flush();
        entityManager.clear();

        City found = entityManager.find(City.class, city.getId());
        assertThat(found.getTourSigunguCode()).isEqualTo("1");
        assertThat(found.getState().getTourAreaCode()).isEqualTo("32");
    }

    @Test
    @DisplayName("같은 시·도 안에서 시군구 코드가 겹치면 거절된다")
    void rejectsDuplicateSigunguCodeInSameState() {
        // 시드를 두 번 돌려도 같은 시·군이 두 행으로 쌓이지 않아야 한다.
        // 중복되면 places.city_id를 이을 때 어느 행에 붙일지 정할 수 없다.
        State gangwon = State.create("강원특별자치도", "32");
        entityManager.persist(gangwon);
        entityManager.persist(City.create(gangwon, "강릉시", "1"));
        entityManager.flush();

        City duplicate = City.create(gangwon, "이름이 달라도 같은 코드다", "1");

        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).hasMessageContaining("uk_cities_state_sigungu");
    }

    @Test
    @DisplayName("시·도가 다르면 같은 시군구 코드를 써도 된다 — 강원 1은 강릉시, 서울 1은 강남구")
    void allowsSameSigunguCodeInDifferentStates() {
        // ⚠️ 이 테스트가 5-8에서 가장 중요하다.
        // 시군구 코드는 시·도 안에서만 유일하다. tour_sigungu_code 단독으로 UNIQUE를 걸면
        // 여기서 깨진다. 지금은 강원만 시드하므로 드러나지 않지만, 전국으로 넓히는 순간 터진다.
        State gangwon = State.create("강원특별자치도", "32");
        State seoul = State.create("서울특별시", "1");
        entityManager.persist(gangwon);
        entityManager.persist(seoul);

        entityManager.persist(City.create(gangwon, "강릉시", "1"));
        entityManager.persist(City.create(seoul, "강남구", "1"));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }
}
