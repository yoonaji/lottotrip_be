package com.lottotrip.place.repository;

import com.lottotrip.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 장소 저장소.
 *
 * <p>반경 내 후보를 찾는 조회는 여기에 아직 없다. 반경 계산(6-2)과 예산·접근성 필터 조건이
 * 함께 정해져야 하므로 <b>6-3에서 추가한다.</b> (tour_api_erd.md 결정 5의 검색 방식 참조)
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * TourAPI 장소 코드로 찾는다. (5-9 적재, 5-10 세부조회)
     *
     * <p>적재는 이 메서드로 "이미 담은 장소인지"를 판단한다. 이름으로 찾으면 같은 이름의 다른 장소를
     * 덮어쓰고, 이름이 바뀐 장소는 못 알아봐 행이 하나 더 생긴다.
     */
    Optional<Place> findByContentId(String contentId);
}
