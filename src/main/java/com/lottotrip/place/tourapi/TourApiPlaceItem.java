package com.lottotrip.place.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 지역 기반 관광 정보 목록의 항목 하나. (roadmap 5-1)
 *
 * TourAPI의 필드명은 전부 소문자 붙여쓰기(`contenttypeid`)라 자바 관례와 맞지 않는다.
 * `@JsonProperty`로 **JSON 이름과 자바 이름을 이어 준다.** 이렇게 해 두면
 * 이 클래스 밖에서는 TourAPI의 작명 습관을 몰라도 된다.
 *
 * Entity가 아니라 별도 record를 두는 이유: 외부 API 응답을 그대로 DB Entity에 받으면
 * **남의 사정이 우리 테이블 구조를 흔든다.** TourAPI가 필드명을 바꾸면 Entity가 따라 바뀌어야 한다.
 * 여기서 한 번 끊어 두면 `PlaceUpserter` 한 곳만 고치면 된다.
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
        String dist,
        @JsonProperty("modifiedtime") String modifiedTime
) {

    /** TourAPI의 수정일시 형식. `"20240115103045"`처럼 구분자 없이 붙여서 온다. */
    private static final DateTimeFormatter MODIFIED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 이 장소 정보가 TourAPI 쪽에서 마지막으로 바뀐 시각.
     *
     * `places.modified_time`에 그대로 담긴다. 지금은 비교에 쓰지 않고 보관만 한다 —
     * 이 값을 기준으로 갱신 대상을 고르던 정기 갱신 배치가 결정 12로 폐기됐다.
     *
     * 형식이 어긋나면 예외 대신 null을 준다. 수정일시 하나 때문에 장소를 통째로 버릴 이유가 없다.
     */
    public LocalDateTime modifiedDateTime() {
        if (modifiedTime == null || modifiedTime.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(modifiedTime.trim(), MODIFIED_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 요청 좌표로부터의 거리(미터). **좌표 기반 조회에서만 채워진다.**
     *
     * 좌표 기반 조회를 쓸 때는 이 값을 그대로 쓴다. 같은 응답의 정렬 순서(가까운 순)가 이 값 기준이라,
     * 우리가 따로 계산하면 지구 반지름 상수나 반올림 차이로 순서와 미세하게 어긋날 수 있기 때문이다.
     *
     * ✅ **슬롯 추첨의 `distanceKm`가 이 값에서 나온다** (결정 12).
     * `SlotService.distanceKmOf()`가 100으로 나눠 소수 첫째 자리로 반올림할 뿐,
     * **거리를 계산하는 코드는 우리 쪽에 없다.**
     * (`Haversine`은 미션 위치 인증에서만 쓴다 — 거기는 TourAPI를 거치지 않아 받을 `dist`가 없다.)
     *
     * 지역 기반 조회(`areaBasedList2`)에는 이 필드가 없으므로 null이다.
     */
    public Double distanceMeters() {
        return toDouble(dist);
    }

    /**
     * 위도. **`mapy`에서 온다.**
     *
     * ⚠️ 이름이 헷갈리는 지점이다. `mapx`/`mapy`는 수학 좌표계의 x·y라서
     * **x = 경도(동서), y = 위도(남북)**다. 흔히 쓰는 "위도·경도" 순서와 반대다.
     * 뒤집어 넣으면 대한민국 장소가 전부 엉뚱한 곳으로 가고, 6단계 반경 검색이 통째로 어긋난다.
     */
    public Double latitude() {
        return toDouble(mapy);
    }

    /** 경도. `mapx`에서 온다. */
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
     * 숫자로 바꾸되, 못 바꾸면 **예외 대신 null**을 준다.
     *
     * 한 건이 깨졌다고 응답 전체가 멈추면 안 된다. `draw`는 한 번에 최대 1,000건을 받는데,
     * 그중 좌표가 이상한 하나 때문에 슬롯 돌리기가 실패하면 사용자가 손해를 본다.
     * null로 두면 `RealtimePlaceFinder`가 "좌표 없는 장소"로 판단해 **추첨 전에** 걸러낸다.
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
