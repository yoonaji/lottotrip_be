package com.lottotrip.slot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 슬롯 돌리기 요청. (tour_api_erd.md 4-2, {@code POST /api/v1/slot/draw})
 *
 * <p><b>여기 없는 두 가지에 주의한다.</b>
 * <ul>
 *   <li><b>반경</b>은 요청 파라미터가 아니다(결정 2). {@code transport}에서 서버가 정한다 —
 *       {@code walk} → 10km, {@code car} → 30km. 프론트가 반경을 정하게 두면
 *       "도보인데 30km" 같은 조합이 들어온다.</li>
 *   <li><b>세션</b>도 요청 파라미터가 아니다(결정 1). 프론트는 세션의 존재를 모르고,
 *       서버가 회원 기준 12시간 find-or-create로 알아서 처리한다.</li>
 * </ul>
 *
 * <p>{@code @NotNull}·{@code @NotBlank}는 컨트롤러에서 {@code @Valid}를 붙이면
 * <b>메서드에 들어오기 전에</b> 검사된다. 그래서 서비스가 "값이 있는지" 확인하는 if문으로 차지 않는다.
 *
 * @param latitude  현재(숙소) 위도
 * @param longitude 현재(숙소) 경도
 * @param budget    예산(원). 등급이 아니라 금액으로 받아 {@code BudgetLevel}이 등급으로 바꾼다
 * @param transport {@code walk}(도보·택시) / {@code car}(자차). 대소문자는 가리지 않는다
 */
public record SlotDrawRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull @PositiveOrZero Integer budget,
        @NotBlank String transport
) {
}
