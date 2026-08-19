package com.lottotrip.common.geo;

/**
 * 반경을 감싸는 위도·경도 사각형. (roadmap 6-3, tour_api_erd.md 결정 5)
 *
 * ⚠️ 과거 배치 방식(결정 10)에서 쓰던 것. 현재 사용 안 함 — 호출처가 테스트뿐.
 *
 * @param minLatitude  남쪽 끝 위도
 * @param maxLatitude  북쪽 끝 위도
 * @param minLongitude 서쪽 끝 경도
 * @param maxLongitude 동쪽 끝 경도
 */
public record BoundingBox(
        double minLatitude,
        double maxLatitude,
        double minLongitude,
        double maxLongitude
) {

    /**위도 1도의 거리(km) 중 가장 짧은 값.*/
    private static final double MIN_KM_PER_DEGREE_LATITUDE = 110.574;

    /** 적도에서 경도 1도의 거리(km). 위도가 높아질수록 `× cos(위도)`만큼 짧아진다. */
    private static final double KM_PER_DEGREE_LONGITUDE_AT_EQUATOR = 111.320;

    /**극 부근에서 0으로 나누는 것을 막는 하한.*/
    private static final double MIN_COSINE = 1e-6;

    /**
     * 중심 좌표에서 반경만큼 떨어진 범위를 감싸는 사각형을 만든다.
     *
     * @param latitude  중심 위도
     * @param longitude 중심 경도
     * @param radiusKm  반경(km). `TransportType`이 정한다 — walk 10 / car 30
     */
    public static BoundingBox around(double latitude, double longitude, double radiusKm) {
        double latitudeDelta = radiusKm / MIN_KM_PER_DEGREE_LATITUDE;

        // 경도 폭은 위도에 따라 달라진다. 사각형의 위·아래 끝에서는 중심보다 위도가 높아
        // 경도 1도가 더 짧아지므로, 그 '가장 불리한 위도'를 기준으로 폭을 잡아야
        // 모서리까지 원을 덮는다. 중심 위도로 계산하면 위아래 구석이 살짝 모자란다.
        double worstCaseLatitude = Math.min(90.0, Math.abs(latitude) + latitudeDelta);
        double cosine = Math.max(MIN_COSINE, Math.cos(Math.toRadians(worstCaseLatitude)));
        double longitudeDelta = radiusKm / (KM_PER_DEGREE_LONGITUDE_AT_EQUATOR * cosine);

        return new BoundingBox(
                Math.max(-90.0, latitude - latitudeDelta),
                Math.min(90.0, latitude + latitudeDelta),
                longitude - longitudeDelta,
                longitude + longitudeDelta);
    }
}
