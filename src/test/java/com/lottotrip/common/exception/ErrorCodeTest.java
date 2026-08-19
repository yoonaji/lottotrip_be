package com.lottotrip.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("모든 ErrorCode는 상태 코드·코드·메시지를 빠짐없이 갖는다")
    void everyErrorCodeIsFullyDefined(ErrorCode errorCode) {
        assertThat(errorCode.getHttpStatus()).isNotNull();
        assertThat(errorCode.getCode()).isNotBlank();
        assertThat(errorCode.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("code 값은 서로 중복되지 않는다 — 개발자 B의 코드가 병합돼도 충돌을 잡아낸다")
    void codesAreUnique() {
        List<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("메시지는 사용자에게 보여줄 문구이므로 한국어 완성형 문장이다")
    void messagesEndWithPeriod() {
        assertThat(ErrorCode.values())
                .allSatisfy(errorCode -> assertThat(errorCode.getMessage()).endsWith("."));
    }
}
