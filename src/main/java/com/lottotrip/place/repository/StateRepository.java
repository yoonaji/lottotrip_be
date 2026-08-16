package com.lottotrip.place.repository;

import com.lottotrip.place.entity.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 광역 지역 저장소. */
public interface StateRepository extends JpaRepository<State, Integer> {

    /**
     * 이름으로 찾는다.
     *
     * ⚠️ **과거 배치 방식(결정 10)에서 쓰던 것. 현재 사용 안 함** — 호출처가 테스트뿐이다.
     * 지역을 이름으로 잇던 시절의 잔재이고, 지금은 아래 코드 기반 조회를 쓴다.
     * 지울지 여부는 사용자에게 확인한다.
     */
    Optional<State> findByStateName(String stateName);

    /**
     * TourAPI 시도 코드로 찾는다. (5-8)
     *
     * TourAPI는 지역을 **코드로만** 주므로 이름으로는 이을 수 없다.
     * `PlaceUpserter.resolveCity()`와 `RegionSeeder`가 모두 이 메서드를 쓴다.
     */
    Optional<State> findByTourAreaCode(String tourAreaCode);
}
