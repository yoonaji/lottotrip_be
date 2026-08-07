package com.lottotrip.auth.entity;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * provider 문자열 변환 검증. (roadmap 4-5)
 *
 * <p>DB 저장값은 대문자({@code KAKAO})인데 API 명세의 요청 본문은 소문자({@code "kakao"})다.
 * (tour_api_erd.md 4-1) 대소문자를 무시해 받으면 양쪽을 모두 수용할 수 있다.
 * {@code TransportType.from()}과 같은 방식이다.
 */
class ProviderTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "KAKAO", "Kakao", "kAkAo"})
    @DisplayName("대소문자를 가리지 않고 변환한다")
    void convertsIgnoringCase(String value) {
        assertThat(ProviderType.from(value)).isEqualTo(ProviderType.KAKAO);
    }

    @Test
    @DisplayName("google·apple도 변환한다")
    void convertsOtherProviders() {
        assertThat(ProviderType.from("google")).isEqualTo(ProviderType.GOOGLE);
        assertThat(ProviderType.from("apple")).isEqualTo(ProviderType.APPLE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"naver", "facebook", " ", "kakao "})
    @DisplayName("정의되지 않은 값이면 BAD_REQUEST")
    void rejectsUnknownValue(String value) {
        // 여기서 걸러 내지 않으면 "구현체를 찾을 수 없다"는 다른 이유로 실패해
        // 잘못 보낸 쪽이 원인을 알기 어렵다.
        assertThatThrownBy(() -> ProviderType.from(value))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
