package com.lottotrip.place.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 지역 기반 관광 정보 목록의 항목 하나. (roadmap 5-1)
 *
 * <p>TourAPI의 필드명은 전부 소문자 붙여쓰기({@code contenttypeid})라 자바 관례와 맞지 않는다.
 * {@code @JsonProperty}로 <b>JSON 이름과 자바 이름을 이어 준다.</b> 이렇게 해 두면
 * 이 클래스 밖에서는 TourAPI의 작명 습관을 몰라도 된다.
 *
 * <p>Entity가 아니라 별도 record를 두는 이유: 외부 API 응답을 그대로 DB Entity에 받으면
 * <b>남의 사정이 우리 테이블 구조를 흔든다.</b> TourAPI가 필드명을 바꾸면 Entity가 따라 바뀌어야 한다.
 * 여기서 한 번 끊어 두면 5-2 적재 코드만 고치면 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiPlaceItem(
        @JsonProperty("contentid") String contentId,
        @JsonProperty("contenttypeid") String contentTypeId,
        String title,
        String addr1,
        String addr2,
        @JsonProperty("areacode") String areaCode,
        @JsonProperty("sigungucode") String sigunguCode,
        String cat1,
        String cat2,
        String cat3,
        @JsonProperty("firstimage") String firstImage,
        @JsonProperty("firstimage2") String firstImage2,
        String mapx,
        String mapy,
        String dist
) {

    /**
     * 요청 좌표로부터의 거리(미터). <b>좌표 기반 조회에서만 채워진다.</b>
     *
     * <p>좌표 기반 조회를 쓸 때는 이 값을 그대로 쓴다. 같은 응답의 정렬 순서(가까운 순)가 이 값 기준이라,
     * 우리가 따로 계산하면 지구 반지름 상수나 반올림 차이로 순서와 미세하게 어긋날 수 있기 때문이다.
     *
     * <p>⚠️ <b>슬롯 추첨은 이 값을 쓰지 않는다.</b> 결정 10으로 추첨이 DB 조회로 바뀌어
     * 받아올 응답 자체가 없다. 추첨의 {@code distanceKm}는 우리가 Haversine으로 직접 계산한다.
     *
     * <p>지역 기반 조회({@code areaBasedList2})에는 이 필드가 없으므로 null이다.
     */
    public Double distanceMeters() {
        return toDouble(dist);
    }

    /**
     * 위도. <b>{@code mapy}에서 온다.</b>
     *
     * <p>⚠️ 이름이 헷갈리는 지점이다. {@code mapx}/{@code mapy}는 수학 좌표계의 x·y라서
     * <b>x = 경도(동서), y = 위도(남북)</b>다. 흔히 쓰는 "위도·경도" 순서와 반대다.
     * 뒤집어 넣으면 대한민국 장소가 전부 엉뚱한 곳으로 가고, 6단계 반경 검색이 통째로 어긋난다.
     */
    public Double latitude() {
        return toDouble(mapy);
    }

    /** 경도. {@code mapx}에서 온다. */
    public Double longitude() {
        return toDouble(mapx);
    }

    /** 기본 주소 + 상세 주소를 이어 붙인다. 둘 다 없으면 null. */
    public String address() {
        String base = addr1 == null ? "" : addr1.trim();
        String detail = addr2 == null ? "" : addr2.trim();
        String joined = (base + " " + detail).trim();
        return joined.isEmpty() ? null : joined;
    }

    /** 대표 이미지. 큰 이미지가 없으면 작은 것이라도 쓴다. */
    public String thumbnailUrl() {
        if (firstImage != null && !firstImage.isBlank()) {
            return firstImage;
        }
        return firstImage2 == null || firstImage2.isBlank() ? null : firstImage2;
    }

    /**
     * 숫자로 바꾸되, 못 바꾸면 <b>예외 대신 null</b>을 준다.
     *
     * <p>수만 건을 훑는 배치에서 한 건이 깨졌다고 전체가 멈추면 안 된다.
     * null로 두면 5-2가 "좌표 없는 장소"로 판단해 건너뛸 수 있다.
     */
    private static Double toDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
