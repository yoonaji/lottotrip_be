package com.lottotrip.common.exception;

import com.lottotrip.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 컨트롤러에서 빠져나온 예외를 한 곳에서 받아 공통 응답 형태로 바꾼다.
 *
 * <p>컨트롤러·서비스는 예외를 잡지 않고 그냥 던지면 된다. 상태 코드와 에러 코드는
 * {@link ErrorCode}가 들고 있으므로 예외 종류가 늘어나도 이 클래스는 커지지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 우리가 의도적으로 던진 예외 — ErrorCode가 상태 코드까지 알고 있다. */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode));
    }

    /** @Valid 검증 실패 — 어느 필드가 틀렸는지는 로그로만 남기고 응답에는 담지 않는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("요청 검증 실패: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST));
    }

    /**
     * 위에서 걸리지 않은 모든 예외.
     *
     * <p>원인은 로그에 전부 남기되 응답에는 고정 메시지만 내려보낸다.
     * 예외 메시지를 그대로 노출하면 내부 구조나 쿼리가 밖으로 새어 나갈 수 있다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
