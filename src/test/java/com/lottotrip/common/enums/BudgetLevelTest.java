package com.lottotrip.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예산 등급 변환 검증.
 *
 * 슬롯 요청은 금액(원)으로 들어오는데(`budget: 50000`) 장소는 등급으로 저장돼 있다.
 * 둘을 이어 주는 변환 규칙을 enum이 직접 갖게 한다. (tour_api_erd.md 2-1의 `BudgetLevel.from`)
 *
 * DB가 필요 없는 순수 계산이므로 컨테이너를 띄우지 않는다.
 */
class BudgetLevelTest {

    @ParameterizedTest(name = "{0}원 → {1}")
    @CsvSource({
            "0,      LOW",
            "10000,  LOW",
            "30000,  LOW",     // 경계값: 3만원까지는 LOW
            "30001,  MEDIUM",
            "50000,  MEDIUM",  // 명세 예시 금액
            "100000, MEDIUM",  // 경계값: 10만원까지는 MEDIUM
            "100001, HIGH",
            "500000, HIGH"
    })
    @DisplayName("금액을 예산 등급으로 변환한다")
    void convertsAmountToLevel(int budget, BudgetLevel expected) {
        assertThat(BudgetLevel.from(budget)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"LOW", "MEDIUM", "HIGH"})
    @DisplayName("모든 등급은 상한 금액을 갖는다")
    void everyLevelHasUpperBound(BudgetLevel level) {
        assertThat(level.getMaxAmount()).isPositive();
    }
}
