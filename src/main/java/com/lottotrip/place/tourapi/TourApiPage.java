package com.lottotrip.place.tourapi;

import java.util.List;

/**
 * 페이지 단위 조회 결과. (roadmap 5-1)
 *
 * 목록을 그냥 `List`로 돌려주면 "더 남았는지"를 알 수 없다.
 * 페이지를 넘겨 가며 훑는 쪽이 그 판단 재료를 함께 받아야 한다.
 *
 * ⚠️ 지금 그렇게 훑는 곳은 없다. 페이지 순회는 **과거 배치 방식(결정 10)**이었고,
 * 결정 12로 `draw`가 1회 호출로 고정되면서 사라졌다. {@link #hasNext()} 참조.
 *
 * ⚠️ 페이지 정보는 응답이 아니라 우리가 보낸 값을 쓴다
 * 예전에는 응답 body의 `pageNo`·`numOfRows`를 그대로 믿었는데,
 * 실측(2026-08-14) 결과 이 API는 두 자리에서 우리 기대와 다르게 응답한다.
 *
 *   - 마지막 페이지는 `numOfRows`에 요청값(100)이 아니라
 *       그 페이지가 실제로 담은 건수를 준다. (강릉 764건 → 8페이지에 `numOfRows: 64`)
 *   - 범위를 넘어선 페이지는 `{"items": "", "numOfRows": 0`}으로 온다.
 *
 * 이 값을 곱셈에 그대로 넣으면 `pageNo × 0 = 0`이 되어 `0 < totalCount`가
 * 영원히 참이 된다. 2026-08-14에 실제로 이 때문에 배치가 무한 루프에 빠져
 * 일일 할당량 1,000회를 통째로 태웠다. 요청한 값은 우리가 알고 있으므로 그것을 쓴다.
 *
 * @param pageNo     우리가 요청한 페이지 번호(1부터)
 * @param numOfRows  우리가 요청한 페이지 크기
 * @param totalCount 조건에 맞는 전체 건수 (이것만은 응답으로만 알 수 있다)
 */
public record TourApiPage<T>(List<T> items, int pageNo, int numOfRows, int totalCount) {

    /**
     * 응답에서 항목과 전체 건수를 뽑고, 페이지 정보는 **요청에 쓴 값**으로 채운다.
     *
     * @param requestedPageNo    이 응답을 받으려고 보낸 페이지 번호
     * @param requestedNumOfRows 이 응답을 받으려고 보낸 페이지 크기
     */
    static <T> TourApiPage<T> from(
            TourApiResponse<T> response, int requestedPageNo, int requestedNumOfRows) {

        TourApiResponse.Body<T> body = response.body();
        return new TourApiPage<>(
                response.items(),
                requestedPageNo,
                requestedNumOfRows,
                orZero(body == null ? null : body.totalCount()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 다음 페이지가 남아 있는지.
     * ⚠️ 과거 배치 방식(결정 10)에서 쓰던 것. 현재 사용 안 함 — 호출처가 테스트뿐이다.
     */
    public boolean hasNext() {
        if (items.isEmpty()) {
            return false;
        }
        return (long) pageNo * numOfRows < totalCount;
    }
}
