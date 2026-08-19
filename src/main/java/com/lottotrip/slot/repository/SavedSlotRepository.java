package com.lottotrip.slot.repository;

import com.lottotrip.slot.entity.SavedSlot;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 슬롯 결과 저장소.
 *
 * `GET /slot/results/{slotId`}와 코스 추가 시의 `slotId → place_id` 조회는
 * 모두 PK 조회라 기본 `findById`로 충분하다.
 */
public interface SavedSlotRepository extends JpaRepository<SavedSlot, Long> {
}
