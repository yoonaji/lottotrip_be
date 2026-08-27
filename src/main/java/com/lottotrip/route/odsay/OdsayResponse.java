package com.lottotrip.route.odsay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * ODsay {@code searchPubTransPathT}(대중교통 경로 조회) 응답.
 *
 * ⚠️ TourAPI와 같은 함정이 있다 — HTTP 200이어도 본문에 {@link Error}가 실려 오면 실패다.
 * {@code result}는 그때 아예 비어 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OdsayResponse(Result result, Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(List<Path> path) {
    }

    /** 경로 후보 1건. {@code OPT=0}(추천순)으로 요청하므로 첫 항목이 곧 추천 경로다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Path(int pathType, Info info, List<SubPath> subPath) {
    }

    /**
     * ⚠️ {@code totalDistance}는 실측(2026-08-27, 강릉시외.고속터미널→경포현대아파트)으로 확인됨 —
     * 이름과 달리 정수가 아니라 {@code 11069.0}처럼 소수로 온다. {@code int}로 선언했다가
     * 실제 호출에서 파싱이 깨질 뻔한 지점이라 {@code double}로 둔다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(int totalTime, int payment, int totalWalk, double totalDistance,
                        int busTransitCount, int subwayTransitCount) {
    }

    /**
     * 경로를 이루는 구간 하나(도보/버스/지하철).
     *
     * @param trafficType 1=지하철, 2=버스, 3=도보
     * @param stationCount 정차 정거장 수. 도보 구간은 오지 않아 null일 수 있다
     * @param lane 이 구간을 탈 수 있는 노선 목록. 도보 구간은 비어 있다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubPath(int trafficType, double distance, int sectionTime, Integer stationCount,
                           String startName, String endName, List<Lane> lane) {
    }

    /** 버스는 {@code busNo}, 지하철은 {@code name}(노선명)으로 온다. 도보 구간은 lane 자체가 없다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lane(String busNo, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String message) {
    }

    /** 결과가 없으면(또는 없어서 result 자체가 비면) 빈 리스트다. */
    public List<Path> paths() {
        return result == null || result.path() == null ? List.of() : result.path();
    }
}
