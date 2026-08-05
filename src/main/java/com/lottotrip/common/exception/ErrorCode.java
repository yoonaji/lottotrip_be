package com.lottotrip.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 공통
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류입니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "COMMON_503", "서비스를 이용할 수 없습니다."),

    // 인증
    INVALID_PROVIDER_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "소셜 토큰이 유효하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "리프레시 토큰이 만료되었거나 유효하지 않습니다."),

    // 슬롯
    NO_PLACE_FOUND(HttpStatus.NOT_FOUND, "SLOT_001", "반경 내 후보 장소가 없습니다."),
    RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "SLOT_002", "슬롯 결과를 찾을 수 없습니다."),

    // 코스
    ALREADY_ADDED(HttpStatus.CONFLICT, "COURSE_001", "이미 코스에 담긴 항목입니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE_002", "코스 항목을 찾을 수 없습니다."),

    // 미션
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "MISSION_001", "미션을 찾을 수 없습니다."),
    ALREADY_COMPLETED(HttpStatus.CONFLICT, "MISSION_002", "이미 완료된 미션입니다."),
    VERIFICATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "MISSION_003", "위치 인증에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
