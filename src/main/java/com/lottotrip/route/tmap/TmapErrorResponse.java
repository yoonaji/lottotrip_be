package com.lottotrip.route.tmap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * T맵 에러 응답. 성공 응답(GeoJSON {@code FeatureCollection})과 완전히 다른 모양이라
 * {@link TmapPedestrianResponse}와 별도 타입으로 둔다.
 *
 * 실측(2026-08-29, 실제 발급 키로 호출):
 * <ul>
 *   <li>{@code category=gw} — 게이트웨이(인증) 단계 거절. 예: appKey가 잘못됐거나
 *       콘솔에서 이 앱에 "보행자 경로안내" 상품이 추가돼 있지 않을 때
 *       {@code {"id":"403","category":"gw","code":"INVALID_API_KEY","message":"Forbidden"}}.
 *       우리 쪽 설정 문제이지 "그런 경로가 없다"는 뜻이 아니다.</li>
 *   <li>{@code category=tmap} — API 로직 단계 거절. {@code code}가 구체적인 사유를 담는다.
 *       경로 자체를 계산할 수 없는 경우(={@link TmapPedestrianClient}의 ROUTE_NOT_FOUND_CODES)만
 *       진짜 "경로 없음"이고, 그 외(예: 필수 파라미터 누락 {@code 9401})는 우리 쪽 요청 결함이다.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapErrorResponse(Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String id, String category, String code, String message) {
    }
}
