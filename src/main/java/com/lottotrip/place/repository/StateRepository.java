package com.lottotrip.place.repository;

import com.lottotrip.place.entity.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 광역 지역 저장소. */
public interface StateRepository extends JpaRepository<State, Integer> {

    /** 지역 데이터를 배치로 적재할 때(5-3) 이미 있는 지역을 다시 넣지 않기 위해 쓴다. */
    Optional<State> findByStateName(String stateName);
}
