package com.lottotrip.common.response;

import com.lottotrip.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 API 응답의 공통 형태.
 *
 * <p>성공·실패와 무관하게 {@code success} / {@code data} / {@code error} 세 필드를 항상 내려준다.
 * 값이 없는 필드를 생략하지 않고 {@code null}로 명시하는 이유는, 프론트가 키의 존재 여부를
 * 신경 쓰지 않고 항상 같은 구조로 디코딩할 수 있게 하기 위함이다. (tour_api_erd.md 3-1 기준)
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorResponse error;

    private ApiResponse(boolean success, T data, ErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null,
                new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorResponse {
        private final String code;
        private final String message;
    }
}
