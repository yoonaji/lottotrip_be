package com.lottotrip.mission.service;

import com.lottotrip.common.geo.Haversine;
import com.lottotrip.place.entity.Place;
import org.springframework.stereotype.Component;

/**미션을 완료할 만큼 그 장소에 가까이 있었는지 판정한다. 거리 기반 미션 인증 (roadmap 8-1, tour_api_erd.md 4-5)*/
@Component
public class MissionLocationVerifier {

    /**인증으로 인정하는 최대 거리(km). 500m (사용자 결정 2026-08-16).*/
    static final double ALLOWED_RADIUS_KM = 0.5;

    /**
     * 이 좌표가 장소의 허용 반경 안인가.
     * 경계(500m) 포함.
     */
    public boolean isAtPlace(Place place, double latitude, double longitude) {
        double distanceKm = Haversine.distanceKm(
                place.getLatitude(), place.getLongitude(), latitude, longitude);
        return distanceKm <= ALLOWED_RADIUS_KM;
    }
}
