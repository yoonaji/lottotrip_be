package com.lottotrip.place.service;

import com.lottotrip.common.geo.BoundingBox;
import com.lottotrip.common.geo.Haversine;
import com.lottotrip.place.dto.PlaceCandidate;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 반경 안의 후보 장소를 찾는다. (roadmap 6-3, 결정 10)
 *
 * <p><b>슬롯 추첨의 후보가 전부 여기서 나온다.</b> 결정 8(온디맨드) 시절에는 이 자리가
 * {@code locationBasedList2} 호출이었지만, 결정 10으로 <b>DB만 본다.</b> TourAPI를 부르지 않는다.
 * 덕분에 draw가 외부 API에 의존하지 않아 응답이 빨라지고 실패 지점이 사라졌다.
 *
 * <h2>두 단계로 찾는다</h2>
 * <ol>
 *   <li><b>사각형으로 좁힌다.</b> {@code idx_places_coordinate}를 태울 수 있는 단순 부등호 비교다.</li>
 *   <li><b>Haversine으로 정확히 잰다.</b> 사각형 모서리에 딸려 온 반경 밖 장소를 걸러내고,
 *       동시에 응답에 나갈 {@code distanceKm}를 얻는다.</li>
 * </ol>
 *
 * <p>순서를 뒤집으면(거리부터 계산) 삼각함수 때문에 인덱스를 타지 못해 매번 전체를 훑는다.
 *
 * <p><b>예산·접근성 필터는 걸지 않는다</b>(결정 9). 예산으로 장소를 거르면 후보가 급격히 줄어
 * {@code NO_PLACE_FOUND}가 사실상 기본 응답이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyPlaceFinder {

    private final PlaceRepository placeRepository;

    /**
     * 기준 좌표에서 반경 안에 있는 장소를 거리와 함께 모두 돌려준다.
     *
     * <p><b>"없음"은 예외가 아니라 빈 목록이다.</b> 후보가 없는 것은 조회가 실패한 것이 아니라
     * 정상적인 결과다. 이것을 {@code NO_PLACE_FOUND}로 바꿀지는 받는 쪽(6-6 draw)이 정한다 —
     * 여기서 예외를 던지면 "없어도 괜찮은" 다른 호출자가 생겼을 때 매번 try-catch를 써야 한다.
     *
     * @param latitude  기준 위도 (세션의 숙소 좌표)
     * @param longitude 기준 경도
     * @param radiusKm  반경(km). {@code TransportType}이 정한다 — walk 10 / car 30
     */
    @Transactional(readOnly = true)
    public List<PlaceCandidate> findWithin(double latitude, double longitude, int radiusKm) {
        BoundingBox box = BoundingBox.around(latitude, longitude, radiusKm);

        List<Place> roughCandidates = placeRepository.findByLatitudeBetweenAndLongitudeBetween(
                box.minLatitude(), box.maxLatitude(), box.minLongitude(), box.maxLongitude());

        List<PlaceCandidate> candidates = roughCandidates.stream()
                .map(place -> new PlaceCandidate(place, Haversine.distanceKm(
                        latitude, longitude, place.getLatitude(), place.getLongitude())))
                // 반경과 같은 거리는 포함한다. 닫아 두면 반경을 딱 채운 장소가 이유 없이 빠진다.
                .filter(candidate -> candidate.distanceKm() <= radiusKm)
                .toList();

        log.debug("반경 조회 — 기준 ({}, {}) / 반경 {}km / 사각형 {}건 → 반경 내 {}건",
                latitude, longitude, radiusKm, roughCandidates.size(), candidates.size());
        return candidates;
    }
}
