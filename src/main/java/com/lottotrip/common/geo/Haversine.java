package com.lottotrip.common.geo;

/**
 * 지구 위 두 좌표 사이의 거리. (roadmap 6-3, tour_api_erd.md 결정 5)
 *
 * **왜 우리가 직접 계산하나.** 결정 8(온디맨드) 시절에는 TourAPI의
 * `locationBasedList2`가 `dist`(요청 좌표로부터의 거리)를 함께 줬다.
 * 결정 10으로 추첨이 DB 조회로 바뀌면서 받아올 응답 자체가 없어졌고,
 * **거리 계산이 우리 몫이 되었다.** 슬롯 응답의 `distanceKm`가 여기서 나온다.
 *
 * **왜 피타고라스로 하지 않나.** 위도·경도는 평면 좌표가 아니다. 경도 1도의 실제 거리는
 * 적도에서 약 111km지만 강원(위도 37.75)에서는 약 88km다. 두 값을 그냥 제곱해 더하면
 * 동서 거리가 20% 넘게 부풀려진다. 반경 10km 판정에서 이 차이는 그대로 오차가 된다.
 *
 * 계산 자체는 무겁지 않지만 **삼각함수라 DB 인덱스를 타지 못한다.** 그래서 이 계산을
 * 바로 쓰지 않고 {@link BoundingBox}로 후보를 좁힌 뒤 2차 검증으로만 쓴다.
 *
 * 8-1(미션 GPS 인증)도 "정해진 반경 안에 있는가"를 판단해야 하므로 이 클래스를 함께 쓴다.
 * 도메인에 매이지 않은 순수 계산이라 `common`에 둔다.
 */
public final class Haversine {

    /**
     * 지구 반지름(km). 평균값이다.
     *
     * 지구는 완전한 구가 아니라 적도 쪽이 조금 부풀어 있다(적도 6,378km / 극 6,357km).
     * 평균값을 쓰면 최대 0.3% 정도 오차가 생기는데, 반경 10km에서 30m 수준이라
     * "이 장소가 반경 안인가"를 가르는 데는 영향이 없다.
     */
    private static final double EARTH_RADIUS_KM = 6371.0;

    /** 인스턴스를 만들 이유가 없는 계산 모음이다. */
    private Haversine() {
    }

    /**
     * 두 좌표 사이의 거리(km).
     *
     * 구 위의 두 점을 잇는 **가장 짧은 곡선(대권거리)**을 잰다. 지표를 따라 재므로
     * 사람이 이동하는 거리 감각과 맞는다.
     *
     * `asin`을 쓰는 형태를 택한 이유: 두 점이 아주 가까울 때 흔히 쓰는
     * `acos` 형태는 부동소수점 오차로 값이 뭉개져 거리가 통째로 0이 되기도 한다.
     * 우리 반경은 10km라 수백 미터 단위가 실제로 쓰이므로 짧은 거리에서 안정적인 쪽을 쓴다.
     */
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
