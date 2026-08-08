package com.lottotrip.place.tourapi;

import java.util.List;

/**
 * 페이지 단위 조회 결과. (roadmap 5-1)
 *
 * <p>목록을 그냥 {@code List}로 돌려주면 <b>"더 남았는지"를 알 수 없다.</b>
 * 5-2 적재 배치는 "다음 페이지가 없을 때까지 반복"으로 돌아가므로 그 판단 재료가 함께 와야 한다.
 *
 * @param pageNo     방금 받은 페이지 번호(1부터)
 * @param numOfRows  한 페이지 크기
 * @param totalCount 조건에 맞는 전체 건수
 */
public record TourApiPage<T>(List<T> items, int pageNo, int numOfRows, int totalCount) {

    /**
     * 응답 껍데기에서 페이지 정보를 뽑아 만든다.
     *
     * <p>숫자 필드가 빠져 올 수 있어 {@code Integer}로 받고 여기서 0으로 맞춘다.
     * 0이면 {@link #hasNext()}가 자연스럽게 false가 되어 배치가 멈춘다 — 안전한 쪽으로 기운다.
     */
    static <T> TourApiPage<T> from(TourApiResponse<T> response) {
        TourApiResponse.Body<T> body = response.body();
        return new TourApiPage<>(
                response.items(),
                orZero(body == null ? null : body.pageNo()),
                orZero(body == null ? null : body.numOfRows()),
                orZero(body == null ? null : body.totalCount()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 다음 페이지가 남아 있는지.
     *
     * <p>"지금까지 받은 건수({@code pageNo × numOfRows})가 전체보다 적으면 남았다"는 계산이다.
     * 예: 100개씩 1페이지를 받았는데 전체가 250건이면 100 &lt; 250 이므로 아직 남았다.
     */
    public boolean hasNext() {
        return (long) pageNo * numOfRows < totalCount;
    }
}
