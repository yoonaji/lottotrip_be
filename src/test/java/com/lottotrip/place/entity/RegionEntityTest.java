package com.lottotrip.place.entity;

import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

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
}
