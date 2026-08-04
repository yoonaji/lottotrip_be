package com.lottotrip.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomExceptionTest {

    @Test
    @DisplayName("던질 때 넘긴 ErrorCode를 그대로 보관한다")
    void holdsErrorCode() {
        CustomException exception = new CustomException(ErrorCode.NO_PLACE_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NO_PLACE_FOUND);
    }

    @Test
    @DisplayName("예외 메시지는 ErrorCode에 정의된 메시지를 사용한다")
    void messageComesFromErrorCode() {
        CustomException exception = new CustomException(ErrorCode.ALREADY_COMPLETED);

        assertThat(exception.getMessage()).isEqualTo("이미 완료된 미션입니다.");
    }

    @Test
    @DisplayName("RuntimeException을 상속해 throws 선언 없이 던질 수 있다")
    void isUncheckedException() {
        CustomException exception = new CustomException(ErrorCode.BAD_REQUEST);

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("던지면 ErrorCode를 꺼내 어떤 에러인지 판별할 수 있다")
    void thrownExceptionExposesErrorCode() {
        assertThatThrownBy(() -> {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        })
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
