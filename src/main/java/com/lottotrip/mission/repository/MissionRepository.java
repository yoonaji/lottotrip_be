package com.lottotrip.mission.repository;

import com.lottotrip.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 미션 마스터 저장소. */
public interface MissionRepository extends JpaRepository<Mission, Long> {

    /**
     * 장소에 등록된 미션을 모두 찾는다.
     *
     * 슬롯이 장소를 뽑은 뒤 이 중 랜덤으로 하나를 고른다. (tour_api_erd.md 2-4 step 8)
     */
    List<Mission> findByPlaceId(Long placeId);

    // 여기 있던 findFirstByPlaceIdOrderByIdAsc는 지웠다(7-6).
    // "그 장소의 첫 미션"으로 draw 때 제시한 미션을 대신하던 임시방편이었는데,
    // 슬롯 조회(6-13)와 코스 조회(7-6) 둘 다 saved_slots.mission_id를 쓰게 되면서 부를 곳이 없어졌다.
}
