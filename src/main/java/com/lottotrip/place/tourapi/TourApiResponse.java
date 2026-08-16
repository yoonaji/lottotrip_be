package com.lottotrip.place.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * TourAPI 응답 껍데기. (roadmap 5-1)
 *
 * 이 API는 어떤 오퍼레이션이든 아래 구조를 그대로 쓴다. 안에 담기는 **항목의 종류만** 다르다.
 *
 * ```
 * { "response": { "header": { "resultCode": "0000" },
 *                 "body":   { "items": { "item": [ ... ] }, "pageNo": 1, "totalCount": 250 } } }
 * ```
 *
 * 그래서 `<T>`(제네릭)로 만들었다. 제네릭은 "담기는 것의 종류를 나중에 정하는 상자"다.
 * 상자를 종류마다 새로 만들지 않아도 `TourApiResponse<TourApiPlaceItem>`,
 * `TourApiResponse<TourApiImageItem>`처럼 필요한 만큼 찍어 쓸 수 있다.
 *
 * ⚠️ **`resultCode`는 HTTP 상태와 별개다.** 이 API는 인증 실패·할당량 초과에도
 * HTTP 200을 주면서 본문 헤더에만 실패 코드를 담는다. 상태 코드만 보면 실패를 성공으로 착각한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiResponse<T>(Response<T> response) {

    /** 정상 처리를 뜻하는 코드. */
    public static final String RESULT_CODE_OK = "0000";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response<T>(Header header, Body<T> body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body<T>(Items<T> items, Integer numOfRows, Integer pageNo, Integer totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items<T>(List<T> item) {
    }

    public boolean isSuccess() {
        return header() != null && RESULT_CODE_OK.equals(header().resultCode());
    }

    public Header header() {
        return response == null ? null : response.header();
    }

    public Body<T> body() {
        return response == null ? null : response.body();
    }

    /**
     * 항목 목록. 어느 단계가 비어 있어도 **빈 리스트**를 돌려준다.
     *
     * `null`을 돌려주면 쓰는 쪽이 매번 null 검사를 해야 하고, 한 곳만 빠뜨려도
     * `NullPointerException`이 난다. "없음"은 null이 아니라 빈 리스트로 표현하는 편이 안전하다.
     *
     * 결과가 0건일 때 `"items": ""`(빈 문자열)로 오는 경우가 있는데,
     * 그건 {@link TourApiClient}가 Jackson 설정으로 null로 바꿔 두므로 여기서 함께 걸러진다.
     */
    public List<T> items() {
        Body<T> body = body();
        if (body == null || body.items() == null || body.items().item() == null) {
            return List.of();
        }
        return body.items().item();
    }
}
