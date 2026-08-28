package com.lottotrip.route.navermap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 네이버 클라우드 플랫폼 Directions 5({@code /driving}) 응답.
 *
 * ODsay와 달리 실패해도 항상 최상위에 {@code code}/{@code message}가 실려 온다 —
 * {@code code == 0}이 성공이고, 그 외에는 실패다. HTTP 상태는 200으로 온다는 점은
 * TourAPI·ODsay와 같은 함정이다.
 *
 * `option=trafast`(최단시간 우선)로만 요청하므로 {@code route.trafast}만 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverDirectionsResponse(int code, String message, Route route) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(List<TrafastRoute> trafast) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrafastRoute(Summary summary) {
    }

    /**
     * @param distance 미터
     * @param duration 밀리초. 분으로 쓰려면 60,000으로 나눠야 한다 — ODsay의 분 단위와 다르다
     * @param tollFare 통행료(원)
     * @param taxiFare 예상 택시요금(원)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(double distance, long duration, int tollFare, int taxiFare) {
    }

    public boolean isSuccess() {
        return code == 0;
    }

    /** 결과가 없으면(또는 실패해서 route 자체가 비면) 빈 리스트다. */
    public List<TrafastRoute> routes() {
        return route == null || route.trafast() == null ? List.of() : route.trafast();
    }
}
