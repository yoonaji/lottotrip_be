package com.lottotrip.place.repository;

import com.lottotrip.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 장소 저장소.
 *
 * <p>⚠️ <b>반경 조회 메서드가 있었으나 결정 12로 지웠다.</b> 추첨이 DB를 훑던 시절에는
 * 위도·경도 사각형으로 1차 필터를 했지만, 이제 후보는 TourAPI가 좌표 기반으로 골라 준다.
 * 되살릴 일이 생기면 {@code common.geo.BoundingBox}가 그대로 남아 있다.
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * TourAPI 장소 코드로 찾는다. (6-12 저장, 6-7 세부조회)
     *
     * <p>저장은 이 메서드로 "이미 담은 장소인지"를 판단한다. 이름으로 찾으면 같은 이름의 다른 장소를
     * 덮어쓰고, 이름이 바뀐 장소는 못 알아봐 행이 하나 더 생긴다.
     */
    Optional<Place> findByContentId(String contentId);
}
