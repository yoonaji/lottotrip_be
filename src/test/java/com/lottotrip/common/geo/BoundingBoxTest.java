package com.lottotrip.common.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반경을 감싸는 위도·경도 사각형 검증. (roadmap 6-3, tour_api_erd.md 결정 5)
 *
 * <p>이 사각형은 <b>1차 필터</b>다. Haversine은 삼각함수라 인덱스를 타지 못해, 바로 계산하면
 * 강원 전체를 매번 훑는다. 사각형으로 {@code idx_places_coordinate}를 태워 수십 건으로 줄인 뒤
 * 정확한 거리를 재는 편이 훨씬 싸다.
 *
 * <p><b>가장 중요한 성질: 사각형이 원을 완전히 감싸야 한다.</b> 조금이라도 작으면
 * 반경 안에 있는 장소가 1차 필터에서 탈락해 <b>영영 뽑히지 않는다.</b> 그 반대(원 밖이 조금
 * 딸려 오는 것)는 2차 Haversine이 걸러 주므로 문제가 되지 않는다. 즉 <b>넉넉한 쪽으로 틀려야 한다.</b>
 */
class BoundingBoxTest {

    private static final double GANGNEUNG_LAT = 37.7519;
    private static final double GANGNEUNG_LNG = 128.8761;

    @Test
    @DisplayName("중심을 감싸는 사각형을 만든다")
    void surroundsCenter() {
        BoundingBox box = BoundingBox.around(GANGNEUNG_LAT, GANGNEUNG_LNG, 10);

        assertThat(box.minLatitude()).isLessThan(GANGNEUNG_LAT);
        assertThat(box.maxLatitude()).isGreaterThan(GANGNEUNG_LAT);
        assertThat(box.minLongitude()).isLessThan(GANGNEUNG_LNG);
        assertThat(box.maxLongitude()).isGreaterThan(GANGNEUNG_LNG);
    }

    @Test
    @DisplayName("반경 위의 어느 방향 지점도 사각형 안에 들어온다 — 후보를 놓치지 않는다")
    void containsEveryPointOnTheCircle() {
        // 이 테스트가 이 클래스의 존재 이유다. 사각형이 조금이라도 작으면
        // 반경 안의 장소가 1차 필터에서 탈락해 영영 뽑히지 않는다.
        // 36방향(10도 간격)으로 반경 위의 점을 만들어 전부 들어오는지 본다.
        int radiusKm = 30;
        BoundingBox box = BoundingBox.around(GANGNEUNG_LAT, GANGNEUNG_LNG, radiusKm);

        for (int bearing = 0; bearing < 360; bearing += 10) {
            double rad = Math.toRadians(bearing);
            // 정북을 0도로 두고 반경만큼 떨어진 지점의 좌표를 구한다.
            double latOffset = (radiusKm * Math.cos(rad)) / 111.0;
            double lngOffset = (radiusKm * Math.sin(rad))
                    / (111.0 * Math.cos(Math.toRadians(GANGNEUNG_LAT)));
            double lat = GANGNEUNG_LAT + latOffset;
            double lng = GANGNEUNG_LNG + lngOffset;

            assertThat(lat)
                    .as("방위 %d도 지점의 위도가 사각형 안에 있어야 한다", bearing)
                    .isBetween(box.minLatitude(), box.maxLatitude());
            assertThat(lng)
                    .as("방위 %d도 지점의 경도가 사각형 안에 있어야 한다", bearing)
                    .isBetween(box.minLongitude(), box.maxLongitude());
        }
    }

    @Test
    @DisplayName("반경이 커지면 사각형도 커진다")
    void growsWithRadius() {
        BoundingBox walk = BoundingBox.around(GANGNEUNG_LAT, GANGNEUNG_LNG, 10);
        BoundingBox car = BoundingBox.around(GANGNEUNG_LAT, GANGNEUNG_LNG, 30);

        assertThat(car.maxLatitude()).isGreaterThan(walk.maxLatitude());
        assertThat(car.maxLongitude()).isGreaterThan(walk.maxLongitude());
    }

    @Test
    @DisplayName("동서 폭이 남북 폭보다 넓다 — 경도선은 극으로 갈수록 모인다")
    void isWiderThanTall() {
        // 위도 37.75에서 경도 1도는 약 88km, 위도 1도는 약 111km다.
        // 같은 거리를 담으려면 경도 쪽 각도가 더 커야 한다. 이걸 놓치고 같은 값을 쓰면
        // 동서로 반경을 채우지 못해 후보를 놓친다.
        BoundingBox box = BoundingBox.around(GANGNEUNG_LAT, GANGNEUNG_LNG, 10);

        double latSpan = box.maxLatitude() - box.minLatitude();
        double lngSpan = box.maxLongitude() - box.minLongitude();

        assertThat(lngSpan).isGreaterThan(latSpan);
    }

    @Test
    @DisplayName("극지방에서도 경도 계산이 무너지지 않는다")
    void survivesNearThePoles() {
        // 극에 가까우면 cos(위도)가 0에 수렴해 경도 폭이 무한대로 발산한다.
        // 우리 서비스는 강원 한정이라 닿을 일이 없지만, 0으로 나누기가 조용히 NaN을 만들면
        // 조회 조건 전체가 무너져 결과가 항상 비게 된다.
        BoundingBox box = BoundingBox.around(89.9, 128.0, 30);

        assertThat(box.minLongitude()).isNotNaN();
        assertThat(box.maxLongitude()).isNotNaN();
        assertThat(box.minLatitude()).isGreaterThanOrEqualTo(-90.0);
        assertThat(box.maxLatitude()).isLessThanOrEqualTo(90.0);
    }
}
