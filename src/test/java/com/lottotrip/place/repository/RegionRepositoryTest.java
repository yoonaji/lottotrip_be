package com.lottotrip.place.repository;

import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.State;
import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지역 조회 검증. (tour_api_erd.md 1 — states / cities)
 *
 * <p>5단계에서 지역 데이터를 배치로 적재할 때, 이미 있는 지역을 다시 넣지 않으려면
 * 이름으로 찾을 수 있어야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegionRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("이름으로 광역 지역을 찾는다")
    void findsStateByName() {
        entityManager.persist(State.create("강원특별자치도"));
        entityManager.flush();

        assertThat(stateRepository.findByStateName("강원특별자치도")).isPresent();
        assertThat(stateRepository.findByStateName("제주특별자치도")).isEmpty();
    }

    @Test
    @DisplayName("광역 지역과 이름으로 시·군을 찾는다")
    void findsCityByStateAndName() {
        State gangwon = State.create("강원특별자치도");
        entityManager.persist(gangwon);
        entityManager.persist(City.create(gangwon, "강릉시"));
        entityManager.flush();

        assertThat(cityRepository.findByStateIdAndCityName(gangwon.getId(), "강릉시")).isPresent();
        assertThat(cityRepository.findByStateIdAndCityName(gangwon.getId(), "속초시")).isEmpty();
    }

    @Test
    @DisplayName("이름이 같아도 광역 지역이 다르면 다른 시·군이다")
    void distinguishesCityByState() {
        // 실제로 "고성군"은 강원과 경남에 모두 있다. 이름만으로 찾으면 엉뚱한 곳에 장소가 붙는다.
        State gangwon = State.create("강원특별자치도");
        State gyeongnam = State.create("경상남도");
        entityManager.persist(gangwon);
        entityManager.persist(gyeongnam);
        entityManager.persist(City.create(gangwon, "고성군"));
        entityManager.flush();

        assertThat(cityRepository.findByStateIdAndCityName(gangwon.getId(), "고성군")).isPresent();
        assertThat(cityRepository.findByStateIdAndCityName(gyeongnam.getId(), "고성군")).isEmpty();
    }
}
