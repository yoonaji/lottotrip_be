package com.lottotrip.common.geo;

/**
 * 반경을 감싸는 위도·경도 사각형. (roadmap 6-3, tour_api_erd.md 결정 5)
 *
 * <p><b>역할은 1차 필터다.</b> {@link Haversine}은 삼각함수라 DB 인덱스를 타지 못해,
 * 바로 계산하면 강원 전체(2,373건)를 매번 훑는다. 사각형은 단순 부등호 비교라
 * {@code idx_places_coordinate}를 태울 수 있다. 수십 건으로 줄인 뒤 정확한 거리를 재는 편이 훨씬 싸다.
 *
 * <h2>넉넉한 쪽으로 틀려야 한다</h2>
 * 이 사각형은 <b>원을 완전히 감싸야</b> 한다. 조금이라도 작으면 반경 안에 있는 장소가
 * 1차 필터에서 탈락해 <b>영영 뽑히지 않는다.</b> 반대로 원 밖이 조금 딸려 오는 것은
 * 2차 Haversine이 걸러 주므로 아무 문제가 없다. 그래서 각 방향의 폭을 계산할 때
 * <b>거리가 가장 짧게 잡히는 값</b>을 분모로 써서 각도를 크게 만든다.
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

    /**
     * 위도 1도의 거리(km) 중 <b>가장 짧은 값</b>.
     *
     * <p>지구가 완전한 구가 아니라 위도 1도는 적도 부근 약 110.57km, 극 부근 약 111.69km로
     * 조금씩 다르다. 작은 값을 분모로 쓰면 나온 각도가 커져 사각형이 넉넉해진다.
     */
    private static final double MIN_KM_PER_DEGREE_LATITUDE = 110.574;

    /** 적도에서 경도 1도의 거리(km). 위도가 높아질수록 {@code × cos(위도)}만큼 짧아진다. */
    private static final double KM_PER_DEGREE_LONGITUDE_AT_EQUATOR = 111.320;

    /**
     * 극 부근에서 0으로 나누는 것을 막는 하한.
     *
     * <p>극에 다가가면 {@code cos(위도)}가 0에 수렴해 경도 폭이 무한대로 발산한다.
     * 그대로 두면 조회 조건이 NaN이 되어 <b>결과가 조용히 항상 비게 된다.</b>
     * 우리 데이터는 강원 한정이라 닿을 일이 없지만, 조용히 무너지는 종류의 실패라 막아 둔다.
     */
    private static final double MIN_COSINE = 1e-6;

    /**
     * 중심 좌표에서 반경만큼 떨어진 범위를 감싸는 사각형을 만든다.
     *
     * @param latitude  중심 위도
     * @param longitude 중심 경도
     * @param radiusKm  반경(km). {@code TransportType}이 정한다 — walk 10 / car 30
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
