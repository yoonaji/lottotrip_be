package com.lottotrip.place.dto;

import com.lottotrip.place.entity.Place;

/**
 * 슬롯 결과 화면에 보여 줄 장소 정보. (roadmap 5-10, 결정 10)
 *
 * 두 곳에서 온 값이 섞여 있다.
 *   - **DB**(`places`) — 이름·분류·좌표·주소. 배치가 미리 담아 둔 것이라 항상 있다
 *   - **TourAPI 실시간 조회** — 소개글·홈페이지. **없을 수 있다**
 *
 * **응답 스키마는 아직 확정이 아니다.** 실시간 조회로 무엇을 실을지(설명·이미지·요금·
 * 무장애 정보) 프론트와 상의해 정하기로 했다(2026-08-13). 지금은 `detailCommon2`가 주는
 * 소개글·홈페이지만 담는다.
 *
 * Entity를 그대로 내보내지 않는 이유: `Place`에는 `contentId`·`budgetTier`처럼
 * **바깥에 나가면 안 되거나 나갈 이유가 없는 값**이 섞여 있다. 또 Entity를 응답에 쓰면
 * 테이블 구조를 바꿀 때마다 API 응답이 따라 바뀐다.
 *
 * @param category         한글 표시명이 나간다(`NATURE_ATTRACTION` → `"자연관광지"`). TourAPI cat2 이름을 그대로 쓴다
 * @param description      TourAPI 소개글. 실시간 조회가 실패하거나 자료가 없으면 null
 * @param homepageUrl      홈페이지 주소. TourAPI는 HTML 링크로 주므로 주소만 뽑아 담는다
 * @param liveDetailLoaded 실시간 조회가 성공했는지. false면 아래 두 값이 비어 있다는 뜻이다.
 *                         이걸 함께 주는 이유는 "자료가 원래 없는 것"과 "지금 못 받아온 것"이
 *                         프론트 입장에서 다르기 때문이다 — 후자는 다시 시도해 볼 만하다
 */
public record PlaceDetail(
        Long placeId,
        String name,
        String category,
        Double latitude,
        Double longitude,
        String address,
        String description,
        String homepageUrl,
        boolean liveDetailLoaded
) {

    /** 실시간 조회 없이 DB 정보만으로 만든다. 바깥 호출이 실패했을 때의 결과다. */
    public static PlaceDetail storedOnly(Place place) {
        return new PlaceDetail(
                place.getId(),
                place.getName(),
                place.getCategory() == null ? null : place.getCategory().getDisplayName(),
                place.getLatitude(),
                place.getLongitude(),
                place.getAddress(),
                null,
                null,
                false);
    }

    /** DB 정보에 실시간으로 받은 소개글·홈페이지를 얹는다. */
    public PlaceDetail withLiveDetail(String description, String homepageUrl) {
        return new PlaceDetail(placeId, name, category, latitude, longitude, address,
                description, homepageUrl, true);
    }
}
