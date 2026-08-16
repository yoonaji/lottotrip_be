package com.lottotrip.place.repository;

import com.lottotrip.place.entity.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 광역 지역 저장소. */
public interface StateRepository extends JpaRepository<State, Integer> {

    /** 지역 데이터를 배치로 적재할 때 이미 있는 지역을 다시 넣지 않기 위해 쓴다. */
    Optional<State> findByStateName(String stateName);

    /**
     * TourAPI 시도 코드로 찾는다. (5-8)
     *
     * 장소 적재는 지역을 **코드로만** 받으므로 이름으로는 이을 수 없다.
     * 시드가 중복 저장을 피할 때도 이 메서드를 쓴다.
     */
    Optional<State> findByTourAreaCode(String tourAreaCode);
}
