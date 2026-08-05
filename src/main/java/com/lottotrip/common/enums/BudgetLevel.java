package com.lottotrip.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 예산 등급. ERD의 {@code budget_level} enum에 대응한다.
 *
 * <p>⚠️ 경계 금액은 잠정값이다. ERD에 값 목록도 기준 금액도 없어 우선 정의했다.
 *
 * <p>슬롯 요청은 금액(원)으로 들어오는데({@code budget: 50000}) 장소는 등급으로 저장돼 있다.
 * 그 변환 규칙을 서비스에 흩어 두면 기준이 여러 벌 생기므로 enum이 직접 갖게 한다.
 */
@Getter
@AllArgsConstructor
public enum BudgetLevel {

    LOW(30_000),
    MEDIUM(100_000),
    HIGH(Integer.MAX_VALUE);

    /** 이 등급에 속하는 최대 금액(원). 이 값 이하면 해당 등급이다. */
    private final int maxAmount;

    /**
     * 금액을 등급으로 바꾼다.
     *
     * <p>선언 순서대로 훑으면서 "금액이 상한 이하인 첫 등급"을 고른다.
     * {@code HIGH}의 상한이 int 최댓값이라 어떤 금액이든 반드시 하나는 걸린다.
     */
    public static BudgetLevel from(int budget) {
        return Arrays.stream(values())
                .filter(level -> budget <= level.maxAmount)
                .findFirst()
                .orElse(HIGH);
    }
}
