package com.lottotrip.slot.entity;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이동수단과 검색 반경 검증. (tour_api_erd.md 결정 2, 2-2)
 *
 * 반경은 요청 파라미터가 아니라 이동수단으로부터 백엔드가 계산한다.
 */
class TransportTypeTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({"walk, WALK", "car, CAR"})
    @DisplayName("요청의 문자열 값을 이동수단으로 변환한다")
    void convertsRequestValue(String requestValue, TransportType expected) {
        assertThat(TransportType.from(requestValue)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource({"WALK, WALK", "Car, CAR"})
    @DisplayName("대소문자가 달라도 변환한다")
    void isCaseInsensitive(String requestValue, TransportType expected) {
        assertThat(TransportType.from(requestValue)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bike", "", " ", "airplane"})
    @DisplayName("정의되지 않은 이동수단은 BAD_REQUEST로 거절한다")
    void rejectsUnknownValue(String requestValue) {
        // 명세: 이동수단 형식 오류는 400 BAD_REQUEST. (tour_api_erd.md 4-3)
        assertThatThrownBy(() -> TransportType.from(requestValue))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("값이 없어도 BAD_REQUEST로 거절한다")
    void rejectsNull() {
        assertThatThrownBy(() -> TransportType.from(null))
                .isInstanceOf(CustomException.class);
    }

    @ParameterizedTest(name = "{0} → {1}km")
    @CsvSource({"WALK, 10", "CAR, 30"})
    @DisplayName("이동수단마다 검색 반경이 정해져 있다")
    void hasSearchRadius(TransportType transport, int expectedKm) {
        // 결정 2: 반경은 요청으로 받지 않고 백엔드가 이동수단에서 정한다.
        // 2026-08-13 회의로 1/20km → 10/30km. 강릉 시내 1km 안에 장소가 7건뿐이라
        // NO_PLACE_FOUND가 기본 응답이 될 상황이었다. walk는 택시 기준으로 재해석했다.
        assertThat(transport.getSearchRadiusKm()).isEqualTo(expectedKm);
    }
}
