package com.lottotrip.mission.service;

import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 GPS 인증 검증. (roadmap 8-1, tour_api_erd.md 4-5)
 *
 * <p><b>DB를 쓰지 않는다.</b> 좌표 두 개를 받아 거리를 재는 순수 계산이라 컨테이너가 필요 없다.
 * 저장소를 끌어오면 테스트가 느려지기만 하고 검증하는 것은 늘지 않는다.
 *
 * <p><b>거리를 도(degree)로 만들어 시험하는 이유.</b> "500m 떨어진 좌표"를 눈대중으로 적으면
 * 경계 근처를 정확히 찌를 수 없다. 남북 방향은 거리와 위도차가 정비례하므로
 * ({@code 거리 = 지구반지름 × 라디안}), 원하는 거리를 위도차로 정확히 환산할 수 있다.
 * 동서 방향은 위도에 따라 축척이 달라져 이 환산이 통하지 않는다.
 */
class MissionLocationVerifierTest {

    /** 사천진해변. 이 좌표를 기준점으로 삼는다. */
    private static final double BASE_LAT = 37.8021;
    private static final double BASE_LNG = 128.8954;

    /**
     * 남북으로 1km 떨어지는 데 필요한 위도차(도).
     *
     * <p>{@code (1 / 6371) 라디안}을 도로 바꾼 값이다. 남북 방향은 경도가 개입하지 않아
     * 거리와 위도차가 정확히 비례한다.
     */
    private static final double DEGREES_PER_KM = 180.0 / (Math.PI * 6371.0);

    private final MissionLocationVerifier verifier = new MissionLocationVerifier();

    /** 기준점에서 정북으로 {@code km}만큼 떨어진 지점의 위도. */
    private double latitudeNorthOf(double km) {
        return BASE_LAT + km * DEGREES_PER_KM;
    }

    private Place placeAtBase() {
        return Place.builder()
                .contentId("TEST-1")
                .name("사천진해변")
                .category(TravelCategory.NATURE_ATTRACTION)
                .latitude(BASE_LAT)
                .longitude(BASE_LNG)
                .build();
    }

    @Test
    @DisplayName("허용 반경은 500m다")
    void allowsFiveHundredMeters() {
        // 명세에 값이 없어 정한 값이다(사용자 결정 2026-08-16). 휴대폰 GPS 오차와
        // 관광지 크기를 함께 감안했다. 상수 하나만 고치면 조정되도록 고정해 둔다.
        assertThat(MissionLocationVerifier.ALLOWED_RADIUS_KM).isEqualTo(0.5);
    }

    @Test
    @DisplayName("장소와 같은 좌표면 인증된다")
    void acceptsExactLocation() {
        assertThat(verifier.isAtPlace(placeAtBase(), BASE_LAT, BASE_LNG)).isTrue();
    }

    @Test
    @DisplayName("반경 안(400m)이면 인증된다")
    void acceptsInsideRadius() {
        assertThat(verifier.isAtPlace(placeAtBase(), latitudeNorthOf(0.4), BASE_LNG)).isTrue();
    }

    @Test
    @DisplayName("반경 밖(600m)이면 인증되지 않는다")
    void rejectsOutsideRadius() {
        assertThat(verifier.isAtPlace(placeAtBase(), latitudeNorthOf(0.6), BASE_LNG)).isFalse();
    }

    @Test
    @DisplayName("경계선(500m)은 인증으로 친다")
    void acceptsExactBoundary() {
        // 경계를 어느 쪽에 붙일지는 정해야만 하는 선택이다. 사용자가 딱 그 거리에 서 있는
        // 일은 드물지만, 정하지 않으면 부동소수점 오차에 따라 결과가 갈린다.
        // "반경 500m 이내"라는 표현에 맞춰 경계를 포함으로 고정한다.
        assertThat(verifier.isAtPlace(placeAtBase(), latitudeNorthOf(0.5), BASE_LNG)).isTrue();
    }

    @Test
    @DisplayName("동서로 떨어져도 반경으로 판정한다 — 위도만 보지 않는다")
    void measuresEastWestDistanceToo() {
        // 경도만 다른 좌표. 위도차가 0이라, 위도만 비교하는 구현이라면 통과해 버린다.
        // 강릉(위도 37.8)에서 경도 0.1도는 약 8.8km다.
        assertThat(verifier.isAtPlace(placeAtBase(), BASE_LAT, BASE_LNG + 0.1)).isFalse();
    }

    @Test
    @DisplayName("아주 먼 곳(서울)이면 인증되지 않는다")
    void rejectsFarAway() {
        assertThat(verifier.isAtPlace(placeAtBase(), 37.5665, 126.9780)).isFalse();
    }
}
