package com.lottotrip.mission.repository;

import com.lottotrip.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 미션 마스터 저장소. */
public interface MissionRepository extends JpaRepository<Mission, Long> {

    /**
     * 장소에 등록된 미션을 모두 찾는다.
     *
     * <p>슬롯이 장소를 뽑은 뒤 이 중 랜덤으로 하나를 고른다. (tour_api_erd.md 2-4 step 8)
     */
    List<Mission> findByPlaceId(Long placeId);

    /**
     * 이 장소에 가장 먼저 등록된 미션. (6-7 결과 조회)
     *
     * <p>⚠️ <b>draw 때 제시한 미션과 같다는 보장은 없다.</b> {@code saved_slots}에 어떤 미션을
     * 보여 줬는지 남기지 않기 때문이다(미확정 — 스키마 변경이 필요하다).
     * 정렬을 못 박아 두는 이유는 <b>같은 슬롯을 여러 번 조회할 때 미션이 바뀌지 않게</b> 하기 위함이다.
     */
    Optional<Mission> findFirstByPlaceIdOrderByIdAsc(Long placeId);
}
