package com.lottotrip.common.response;

import com.lottotrip.common.error.ErrorCode;

public record ApiResponse<T>(int status, T data, ApiError error) {

    public static <T> ApiResponse<T> success(int httpStatus, T data) {
        return new ApiResponse<>(httpStatus, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(
                errorCode.getHttpStatus().value(),
                null,
                new ApiError(errorCode.getCode(), errorCode.getMessage())
        );
    }
}
