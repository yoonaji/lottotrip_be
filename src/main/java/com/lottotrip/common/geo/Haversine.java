package com.lottotrip.common.geo;

/**
 * 두 좌표 사이의 거리. (roadmap 6-3, tour_api_erd.md 결정 5)
 *
 * 지금 쓰는 곳은 미션 위치 인증({@code MissionLocationVerifier}) 하나뿐.
 *
 * 도메인에 매이지 않은 순수 계산이라 `common`에 둔다.
 */
public final class Haversine {

    /**지구 반지름(km)*/
    private static final double EARTH_RADIUS_KM = 6371.0;

    /** 인스턴스를 만들 이유가 없는 계산 모음. */
    private Haversine() {
    }

    /**두 좌표 사이의 거리(km).*/
    public static double distanceKm(double latitude1, double longitude1,
                                    double latitude2, double longitude2) {

        double deltaLat = Math.toRadians(latitude2 - latitude1);
        double deltaLng = Math.toRadians(longitude2 - longitude1);

        double sinHalfLat = Math.sin(deltaLat / 2);
        double sinHalfLng = Math.sin(deltaLng / 2);

        // 두 점 사이 중심각의 절반에 대한 sin². 위도가 높을수록 경도 차이의 영향이
        // 줄어드는 것이 cos·cos 항으로 자연히 반영된다.
        double a = sinHalfLat * sinHalfLat
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * sinHalfLng * sinHalfLng;

        // 부동소수점 오차로 1을 아주 살짝 넘으면 asin이 NaN을 준다. 그 자리에서 막는다.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
