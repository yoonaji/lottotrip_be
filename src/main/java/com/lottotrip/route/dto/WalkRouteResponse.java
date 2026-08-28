package com.lottotrip.route.dto;

import com.lottotrip.route.tmap.TmapPedestrianResponse;

/**
 * 슬롯을 돌린 출발지에서 당첨 장소까지의 도보 경로.
 *
 * @param totalDistanceMeters 미터
 */
public record WalkRouteResponse(int totalMinutes, int totalDistanceMeters) {

    private static final int SECONDS_PER_MINUTE = 60;

    public static WalkRouteResponse from(TmapPedestrianResponse.Properties properties) {
        return new WalkRouteResponse(properties.totalTime() / SECONDS_PER_MINUTE, properties.totalDistance());
    }
}
