package com.lottotrip.place.repository;

import com.lottotrip.place.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 시·군·구 저장소. */
public interface CityRepository extends JpaRepository<City, Integer> {

    /**
     * 광역 지역과 이름으로 시·군을 찾는다.
     *
     * ⚠️ **과거 배치 방식(결정 10)에서 쓰던 것. 현재 사용 안 함** — 호출처가 테스트뿐이다.
     * 지금은 아래 코드 기반 조회를 쓴다. 지울지 여부는 사용자에게 확인한다.
     *
     * 이름만으로 찾으면 안 된다. "고성군"은 강원과 경남에 모두 있어서, 엉뚱한
     * 광역 지역에 장소가 붙을 수 있다.
     *
     * `StateId`는 "state 필드가 가진 객체의 id"를 뜻한다. 점을 찍지 않고 이어 쓴다.
     */
    Optional<City> findByStateIdAndCityName(Integer stateId, String cityName);

    /**
     * 광역 지역과 TourAPI 시군구 코드로 찾는다. (5-8)
     *
     * ⚠️ **코드만으로 찾으면 안 된다.** 시군구 코드는 시·도 안에서만 유일해서,
     * 강원의 `"1"`은 강릉시지만 서울의 `"1"`은 강남구다.
     * 코드만 보고 찾으면 강남구 장소가 강릉에 붙는다.
     */
    Optional<City> findByStateIdAndTourSigunguCode(Integer stateId, String tourSigunguCode);
}
