package com.lottotrip.route.dto;

import com.lottotrip.route.odsay.OdsayResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 슬롯을 돌린 출발지에서 당첨 장소까지의 대중교통 경로. ODsay 원본 응답 대신
 * 프론트가 실제로 그릴 화면(탭·구간 목록)에 필요한 값만 추려 내보낸다.
 *
 * @param legs 도보→버스→도보처럼 이어지는 구간들. 순서 그대로 화면에 나열하면 된다
 */
public record RouteResponse(int totalMinutes, int payment, List<RouteLeg> legs) {

    private static final int TRAFFIC_TYPE_SUBWAY = 1;
    private static final int TRAFFIC_TYPE_BUS = 2;

    /**
     * @param mode         WALK / BUS / SUBWAY
     * @param routeName    버스 번호("92, 9") 또는 지하철 노선명. 도보 구간은 null
     * @param stationCount 정차 정거장 수. 도보 구간은 null
     */
    public record RouteLeg(String mode, String routeName, String startName, String endName,
                            Integer stationCount, int sectionMinutes) {
    }

    public static RouteResponse from(OdsayResponse.Path path) {
        List<RouteLeg> legs = path.subPath() == null
                ? List.of()
                : path.subPath().stream().map(RouteResponse::toLeg).toList();
        return new RouteResponse(path.info().totalTime(), path.info().payment(), legs);
    }

    private static RouteLeg toLeg(OdsayResponse.SubPath subPath) {
        return new RouteLeg(
                modeOf(subPath.trafficType()),
                routeNameOf(subPath),
                subPath.startName(),
                subPath.endName(),
                subPath.stationCount(),
                subPath.sectionTime());
    }

    private static String modeOf(int trafficType) {
        if (trafficType == TRAFFIC_TYPE_SUBWAY) {
            return "SUBWAY";
        }
        if (trafficType == TRAFFIC_TYPE_BUS) {
            return "BUS";
        }
        return "WALK";
    }

    /** 버스는 lane[].busNo, 지하철은 lane[].name으로 온다. 도보 구간은 lane 자체가 없다. */
    private static String routeNameOf(OdsayResponse.SubPath subPath) {
        if (subPath.lane() == null || subPath.lane().isEmpty()) {
            return null;
        }
        String joined = subPath.lane().stream()
                .map(lane -> lane.busNo() != null ? lane.busNo() : lane.name())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }
}
