package com.lottotrip.place.service;

import com.lottotrip.place.dto.PlaceCandidate;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 반경 내 후보 조회 검증. (roadmap 6-3, 결정 10)
 *
 * <p><b>슬롯 추첨의 후보가 전부 여기서 나온다.</b> 결정 10으로 draw가 TourAPI를 부르지 않게 되면서
 * 이 조회가 잘못되면 사용자는 엉뚱한 거리의 장소를 받거나 아무것도 받지 못한다.
 *
 * <p>DB는 진짜를 쓴다. 사각형 1차 필터가 실제 쿼리로 도는지, 그 결과가 Haversine과 맞물리는지는
 * 진짜 조회가 일어나야 확인된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NearbyPlaceFinderTest extends PostgresContainerSupport {

    /** 강릉시청 부근. 모든 거리의 기준점이다. */
    private static final double CENTER_LAT = 37.7519;
    private static final double CENTER_LNG = 128.8761;

    @Autowired
    private PlaceRepository placeRepository;

    private NearbyPlaceFinder finder;
    private int sequence;

    @BeforeEach
    void setUp() {
        finder = new NearbyPlaceFinder(placeRepository);
        sequence = 0;
    }

    /** 좌표만 다른 장소를 하나 만든다. content_id는 NOT NULL·UNIQUE라 매번 새 값을 준다. */
    private Place placeAt(String name, double latitude, double longitude) {
        return placeRepository.save(Place.builder()
                .contentId("test-" + (++sequence))
                .contentTypeId("12")
                .name(name)
                .category(TravelCategory.NATURE)
                .address("강원특별자치도 강릉시")
                .latitude(latitude)
                .longitude(longitude)
                .build());
    }

    @Test
    @DisplayName("반경 안의 장소를 거리와 함께 돌려준다")
    void findsPlacesWithinRadius() {
        placeAt("가까운 곳", CENTER_LAT + 0.01, CENTER_LNG);

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).place().getName()).isEqualTo("가까운 곳");
        // 위도 0.01도 ≈ 1.11km
        assertThat(found.get(0).distanceKm()).isCloseTo(1.11, within(0.05));
    }

    @Test
    @DisplayName("반경 밖의 장소는 빼고 준다")
    void excludesPlacesOutsideRadius() {
        placeAt("반경 안", CENTER_LAT + 0.01, CENTER_LNG);      // 약 1.1km
        placeAt("반경 밖", CENTER_LAT + 0.5, CENTER_LNG);       // 약 55km

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).place().getName()).isEqualTo("반경 안");
    }

    @Test
    @DisplayName("사각형 모서리에 걸린 장소는 Haversine이 걸러낸다")
    void excludesCornerOfBoundingBox() {
        // 이 테스트가 6-3의 핵심이다. 사각형만으로 거르면 모서리 장소가 그대로 통과한다.
        // 북동쪽으로 각각 반경만큼 떨어진 지점은 사각형 '안'이지만 실제 거리는
        // 약 14.1km(= √(10² + 10²))라 반경 10km 밖이다.
        double latOffset = 10.0 / 111.0;
        double lngOffset = 10.0 / (111.0 * Math.cos(Math.toRadians(CENTER_LAT)));
        placeAt("모서리", CENTER_LAT + latOffset, CENTER_LNG + lngOffset);

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).isEmpty();
    }

    /**
     * 위도 1도의 거리(km). {@code 지구반지름 × π / 180}으로, Haversine이 실제로 쓰는 값이다.
     *
     * <p>어림값(111.19 등)을 쓰면 "정확히 10km"를 노린 좌표가 10.0004km가 되어
     * 경계 판정이 뒤집힌다. 경계를 다루는 테스트에서는 계산과 같은 기준을 써야 한다.
     */
    private static final double KM_PER_DEGREE_LATITUDE = 6371.0 * Math.PI / 180.0;

    @Test
    @DisplayName("반경을 거의 채운 장소는 포함한다")
    void includesPlaceJustInsideBoundary() {
        // 경계를 닫아 두면(< 로 쓰면) 반경을 딱 채운 장소가 이유 없이 빠진다.
        // 부동소수점으로 '정확히 10.000km'를 만들 수는 없으므로 아주 조금 안쪽에 둔다.
        placeAt("경계 안쪽", CENTER_LAT + (9.99 / KM_PER_DEGREE_LATITUDE), CENTER_LNG);

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).distanceKm()).isCloseTo(9.99, within(0.01));
    }

    @Test
    @DisplayName("반경을 조금이라도 넘으면 제외한다")
    void excludesPlaceJustOutsideBoundary() {
        // 사각형 1차 필터는 이 장소를 통과시킨다(정북 방향이라 사각형 안이다).
        // 2차 Haversine이 반경 밖임을 알아내 걸러야 한다.
        placeAt("경계 바깥", CENTER_LAT + (10.01 / KM_PER_DEGREE_LATITUDE), CENTER_LNG);

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("후보가 없으면 예외가 아니라 빈 목록이다")
    void returnsEmptyWhenNothingNearby() {
        // "없음"은 오류가 아니라 정상적인 조회 결과다. NO_PLACE_FOUND로 바꿀지는
        // 이 결과를 받는 쪽(6-6 draw)이 정한다. 조회가 예외를 던지면
        // "없어도 괜찮은" 다른 호출자가 생겼을 때 매번 try-catch를 써야 한다.
        placeAt("멀리", CENTER_LAT + 1.0, CENTER_LNG + 1.0);

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("DB가 비어 있어도 터지지 않는다")
    void survivesEmptyDatabase() {
        assertThat(finder.findWithin(CENTER_LAT, CENTER_LNG, 10)).isEmpty();
    }

    @Test
    @DisplayName("반경이 넓어지면 후보가 늘어난다 — walk 10km / car 30km")
    void widerRadiusFindsMore() {
        placeAt("5km", CENTER_LAT + (5.0 / 111.19), CENTER_LNG);
        placeAt("20km", CENTER_LAT + (20.0 / 111.19), CENTER_LNG);

        assertThat(finder.findWithin(CENTER_LAT, CENTER_LNG, 10)).hasSize(1);
        assertThat(finder.findWithin(CENTER_LAT, CENTER_LNG, 30)).hasSize(2);
    }

    @Test
    @DisplayName("여러 후보를 모두 돌려준다 — 추첨은 받는 쪽이 한다")
    void returnsAllCandidates() {
        placeAt("A", CENTER_LAT + 0.01, CENTER_LNG);
        placeAt("B", CENTER_LAT + 0.02, CENTER_LNG);
        placeAt("C", CENTER_LAT - 0.01, CENTER_LNG);

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).hasSize(3)
                .extracting(candidate -> candidate.place().getName())
                .containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    @DisplayName("동서로 떨어진 장소도 반경 안이면 찾는다")
    void findsPlacesToTheEastAndWest() {
        // 경도 폭을 위도와 같게 잡는 실수를 하면 동서 방향 후보가 통째로 빠진다.
        // 위도 37.75에서 경도 1도는 약 88km라 같은 거리라도 각도가 더 크다.
        double eastOffset = 8.0 / (111.32 * Math.cos(Math.toRadians(CENTER_LAT)));
        placeAt("동쪽 8km", CENTER_LAT, CENTER_LNG + eastOffset);
        placeAt("서쪽 8km", CENTER_LAT, CENTER_LNG - eastOffset);

        List<PlaceCandidate> found = finder.findWithin(CENTER_LAT, CENTER_LNG, 10);

        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(candidate ->
                assertThat(candidate.distanceKm()).isCloseTo(8.0, within(0.1)));
    }
}
