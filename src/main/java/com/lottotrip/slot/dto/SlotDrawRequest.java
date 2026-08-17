package com.lottotrip.slot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 슬롯 돌리기 요청. (tour_api_erd.md 4-2, `POST /api/v1/slot/draw`)
 *
 * 여기 없는 두 가지에 주의한다.
 *   - 반경은 요청 파라미터가 아니다(결정 2). `transport`에서 서버가 정한다 —
 *       `walk` → 10km, `car` → 30km. 프론트가 반경을 정하게 두면
 *       "도보인데 30km" 같은 조합이 들어온다.
 *   - 세션도 요청 파라미터가 아니다(결정 1). 프론트는 세션의 존재를 모르고,
 *       서버가 회원 기준 12시간 find-or-create로 알아서 처리한다.
 *
 * `@NotNull`·`@NotBlank`는 컨트롤러에서 `@Valid`를 붙이면
 * 메서드에 들어오기 전에 검사된다. 그래서 서비스가 "값이 있는지" 확인하는 if문으로 차지 않는다.
 *
 * @param latitude      현재(숙소) 위도
 * @param longitude     현재(숙소) 경도
 * @param budget        예산(원). 등급이 아니라 금액으로 받아 `BudgetLevel`이 등급으로 바꾼다.
 *                      ⚠️ 추첨 필터로는 쓰지 않는다(결정 9) — 세션에 기록만 된다
 * @param transport     `walk`(도보·택시) / `car`(자차). 대소문자는 가리지 않는다
 * @param accessible    무장애 정보가 등록된 장소만 뽑을지. 안 보내면 `false` (roadmap 6-13)
 * @param contentTypeId 관광 타입 코드(12=관광지, 39=음식점 …). 안 보내면 전 종류(결정 13).
 *                      ⚠️ `32`(숙박)를 보내면 후보가 전부 걸러져 `NO_PLACE_FOUND`다(결정 18)
 */
public record SlotDrawRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull @PositiveOrZero Integer budget,
        @NotBlank String transport,
        Boolean accessible,
        String contentTypeId
) {

    /**
     * 안 보냈을 때의 기본값을 여기서 정한다.
     *
     * record의 compact 생성자는 값이 필드에 담기기 직전에 끼어든다.
     * 여기서 채워 두면 이 값을 쓰는 쪽이 매번 null을 확인하지 않아도 된다.
     * 서비스에 `request.accessible() != null && request.accessible()` 같은 코드가 흩어지면
     * 한 곳에서 빠뜨렸을 때 조용히 다르게 동작한다.
     */
    public SlotDrawRequest {
        if (accessible == null) {
            accessible = false;
        }
    }
}
