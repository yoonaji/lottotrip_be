package com.lottotrip.common.exception;

import com.lottotrip.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 컨트롤러에서 빠져나온 예외를 한 곳에서 받아 공통 응답 형태로 바꾼다.
 *
 * <p>컨트롤러·서비스는 예외를 잡지 않고 그냥 던지면 된다. 상태 코드와 에러 코드는
 * {@link ErrorCode}가 들고 있으므로 예외 종류가 늘어나도 이 클래스는 커지지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** 우리가 의도적으로 던진 예외 — ErrorCode가 상태 코드까지 알고 있다. */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode));
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

    /**
     * Spring MVC가 직접 던지는 표준 예외(본문 파싱 실패, 지원하지 않는 메서드·미디어타입,
     * {@code @Valid} 검증 실패 등)의 최종 응답을 만드는 지점.
     *
     * <p>부모가 상태 코드는 이미 알맞게 정해주지만 본문은 Spring 기본 형식({@code ProblemDetail})이라
     * 공통 응답 포맷과 어긋난다. 그래서 상태 코드는 그대로 두고 본문만 {@link ApiResponse}로 교체한다.
     *
     * <p>클라이언트 잘못(4xx)은 스택트레이스가 의미 없으므로 warn으로만 남긴다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ErrorCode errorCode = statusCode.is4xxClientError()
                ? ErrorCode.BAD_REQUEST
                : ErrorCode.INTERNAL_ERROR;

        log.warn("요청 처리 실패 ({} {}): {}", statusCode, ex.getClass().getSimpleName(), ex.getMessage());

        return super.handleExceptionInternal(ex, ApiResponse.fail(errorCode), headers, statusCode, request);
    }
}
