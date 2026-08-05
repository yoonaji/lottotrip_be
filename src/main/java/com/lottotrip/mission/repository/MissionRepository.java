package com.lottotrip.mission.repository;

import com.lottotrip.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 미션 마스터 저장소. */
public interface MissionRepository extends JpaRepository<Mission, Long> {

    /**
     * 장소에 등록된 미션을 모두 찾는다.
     *
     * <p>슬롯이 장소를 뽑은 뒤 이 중 랜덤으로 하나를 고른다. (tour_api_erd.md 2-4 step 8)
     */
    List<Mission> findByPlaceId(Long placeId);
}
