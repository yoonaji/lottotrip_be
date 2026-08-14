package com.lottotrip.common.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 두 좌표 사이 거리 계산 검증. (roadmap 6-3, tour_api_erd.md 결정 5)
 *
 * <p>결정 10으로 추첨이 DB에서 이뤄지면서 TourAPI가 주던 {@code dist}를 못 받게 됐다.
 * <b>거리 계산이 우리 몫이 되었고</b>, 슬롯 응답의 {@code distanceKm}가 여기서 나온다.
 * 값이 틀리면 "반경 10km"라고 해 놓고 엉뚱한 거리의 장소가 뽑힌다.
 */
class HaversineTest {

    @Test
    @DisplayName("같은 지점 사이의 거리는 0이다")
    void zeroForSamePoint() {
        assertThat(Haversine.distanceKm(37.7519, 128.8761, 37.7519, 128.8761))
                .isCloseTo(0.0, within(0.001));
    }

    @Test
    @DisplayName("위도 1도 차이는 약 111km다")
    void oneDegreeOfLatitudeIsAbout111km() {
        // 위도 1도는 지구 어디서나 거의 같다(약 111km). 계산이 맞는지 가늠하는 기준점이다.
        double distance = Haversine.distanceKm(37.0, 128.0, 38.0, 128.0);

        assertThat(distance).isCloseTo(111.19, within(0.5));
    }

    @Test
    @DisplayName("경도 1도 차이는 위도가 높을수록 짧아진다")
    void oneDegreeOfLongitudeShrinksWithLatitude() {
        // 경도선은 극으로 갈수록 모인다. 적도에서 약 111km, 강원(위도 37.75)에서는 약 88km.
        // 이 성질을 놓치면 사각형 범위를 만들 때 동서 폭이 모자라 후보를 놓친다.
        double atEquator = Haversine.distanceKm(0.0, 128.0, 0.0, 129.0);
        double atGangwon = Haversine.distanceKm(37.75, 128.0, 37.75, 129.0);

        assertThat(atEquator).isCloseTo(111.19, within(0.5));
        assertThat(atGangwon).isCloseTo(87.9, within(0.5));
        assertThat(atGangwon).isLessThan(atEquator);
    }

    @Test
    @DisplayName("실제 두 도시 사이 거리와 맞는다")
    void matchesKnownCityDistance() {
        // 서울시청 ↔ 부산시청. 실제 직선거리는 약 325km로 알려져 있다.
        double distance = Haversine.distanceKm(37.5665, 126.9780, 35.1796, 129.0756);

        assertThat(distance).isCloseTo(325.0, within(3.0));
    }

    @Test
    @DisplayName("방향을 바꿔도 거리는 같다")
    void isSymmetric() {
        double forward = Haversine.distanceKm(37.7519, 128.8761, 37.8021, 128.9089);
        double backward = Haversine.distanceKm(37.8021, 128.9089, 37.7519, 128.8761);

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    @DisplayName("아주 가까운 두 지점도 0이 되지 않는다")
    void handlesShortDistances() {
        // 슬롯 반경이 10km라 수백 미터 단위가 실제로 쓰인다.
        // 부동소수점 처리가 엉성하면 짧은 거리가 통째로 0으로 뭉개진다.
        double distance = Haversine.distanceKm(37.7519, 128.8761, 37.7529, 128.8761);

        assertThat(distance).isGreaterThan(0.1).isLessThan(0.2);
    }
}
