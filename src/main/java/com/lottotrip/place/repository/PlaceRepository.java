package com.lottotrip.place.repository;

import com.lottotrip.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 장소 저장소.
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * TourAPI 장소 코드로 찾는다. (5-9 적재, 5-10 세부조회)
     *
     * <p>적재는 이 메서드로 "이미 담은 장소인지"를 판단한다. 이름으로 찾으면 같은 이름의 다른 장소를
     * 덮어쓰고, 이름이 바뀐 장소는 못 알아봐 행이 하나 더 생긴다.
     */
    Optional<Place> findByContentId(String contentId);

    /**
     * 위도·경도 사각형 안의 장소를 모두 찾는다. <b>반경 조회의 1차 필터다.</b> (6-3)
     *
     * <p>이 메서드는 <b>원이 아니라 사각형</b>을 조회하므로 모서리에 반경 밖 장소가 딸려 온다.
     * 정확한 거리 판정은 부르는 쪽({@code NearbyPlaceFinder})이 Haversine으로 마무리한다.
     *
     * <p><b>왜 SQL에서 거리까지 계산하지 않나.</b> 삼각함수를 조건에 쓰면
     * {@code idx_places_coordinate} 인덱스를 타지 못해 매번 전체를 훑게 된다.
     * 단순 부등호 비교만 남겨 두어야 인덱스가 일한다.
     *
     * <p>{@code Between}은 양 끝을 포함한다(SQL의 {@code BETWEEN}과 같다).
     * 경계에 걸친 장소가 빠지지 않아 사각형이 원을 감싼다는 성질이 유지된다.
     */
    List<Place> findByLatitudeBetweenAndLongitudeBetween(
            double minLatitude, double maxLatitude, double minLongitude, double maxLongitude);
}
