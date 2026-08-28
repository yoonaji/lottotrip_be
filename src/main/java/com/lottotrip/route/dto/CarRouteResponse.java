package com.lottotrip.route.dto;

import com.lottotrip.route.navermap.NaverDirectionsResponse;

/**
 * 슬롯을 돌린 출발지에서 당첨 장소까지의 자동차 경로.
 *
 * @param totalDistanceMeters 미터
 * @param tollFare            통행료(원)
 * @param taxiFare            예상 택시요금(원)
 */
public record CarRouteResponse(int totalMinutes, double totalDistanceMeters, int tollFare, int taxiFare) {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    public static CarRouteResponse from(NaverDirectionsResponse.TrafastRoute route) {
        NaverDirectionsResponse.Summary summary = route.summary();
        int minutes = (int) (summary.duration() / MILLIS_PER_MINUTE);
        return new CarRouteResponse(minutes, summary.distance(), summary.tollFare(), summary.taxiFare());
    }
}
