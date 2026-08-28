package com.lottotrip.route.tmap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * T맵 보행자 경로안내 응답. GeoJSON {@code FeatureCollection} 형태로 온다.
 *
 * ⚠️ 요약 정보(전체 거리·시간)가 별도 필드가 아니라 **첫 번째 feature의 properties**에
 * 실려 온다 — ODsay·NCP처럼 최상위에 summary 객체가 따로 있는 구조가 아니다. 나머지
 * feature들은 구간별 안내(정류장·회전 지점 등)라 지금은 쓰지 않는다.
 *
 * geometry(Point/LineString)는 지도에 선을 그릴 때나 필요해서 지금은 매핑하지 않는다 —
 * ignoreUnknown이 알아서 걸러낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapPedestrianResponse(String type, List<Feature> features) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(Properties properties) {
    }

    /** 전체 요약이 담긴 feature만 이 두 필드가 채워져 있다. 나머지는 null이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(Integer totalDistance, Integer totalTime) {
    }

    public List<Feature> featureList() {
        return features == null ? List.of() : features;
    }
}
