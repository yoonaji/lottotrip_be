package com.lottotrip.place.repository;

import com.lottotrip.place.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 시·군·구 저장소. */
public interface CityRepository extends JpaRepository<City, Integer> {

    /**
     * 광역 지역과 이름으로 시·군을 찾는다.
     *
     * <p>이름만으로 찾으면 안 된다. "고성군"은 강원과 경남에 모두 있어서, 적재할 때 엉뚱한
     * 광역 지역에 장소가 붙을 수 있다.
     *
     * <p>{@code StateId}는 "state 필드가 가진 객체의 id"를 뜻한다. 점을 찍지 않고 이어 쓴다.
     */
    Optional<City> findByStateIdAndCityName(Integer stateId, String cityName);
}
