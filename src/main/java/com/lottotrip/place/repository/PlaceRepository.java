package com.lottotrip.place.repository;

import com.lottotrip.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 저장소.
 *
 * <p>반경 내 후보를 찾는 조회는 여기에 아직 없다. 반경 계산(6-2)과 예산·접근성 필터 조건이
 * 함께 정해져야 하므로 <b>6-3에서 추가한다.</b> (tour_api_erd.md 결정 5의 검색 방식 참조)
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {
}
