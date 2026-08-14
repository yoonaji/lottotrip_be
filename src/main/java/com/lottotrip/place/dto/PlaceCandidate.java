package com.lottotrip.place.dto;

import com.lottotrip.place.entity.Place;

/**
 * 반경 안에서 찾은 후보 장소 하나와 그 거리. (roadmap 6-3)
 *
 * <p><b>거리를 함께 들고 다니는 이유:</b> 슬롯 응답에 {@code distanceKm}가 들어가는데,
 * 추첨이 끝난 뒤 다시 계산하면 같은 값을 두 번 구하게 된다. 더 나쁜 것은 두 계산이
 * 어긋날 여지가 생긴다는 점이다 — 후보를 고를 때 쓴 거리와 사용자에게 보여 주는 거리가
 * 다르면 "반경 10km라면서 12km 장소가 나왔다"처럼 보인다.
 *
 * <p>{@code Place} 엔티티를 그대로 담고 있으므로 <b>응답에 직접 쓰지 않는다.</b>
 * 이것은 서비스 안에서만 도는 중간 결과이고, 밖으로 나가는 응답은 6-6에서 따로 만든다.
 *
 * @param place      후보 장소
 * @param distanceKm 기준 좌표로부터의 거리(km). 반올림하지 않은 원값이다
 */
public record PlaceCandidate(Place place, double distanceKm) {
}
