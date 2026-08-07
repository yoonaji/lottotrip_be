package com.lottotrip.auth.dto;

/**
 * 로그아웃 응답. (tour_api_erd.md 4-1)
 *
 * <p>돌려줄 데이터가 메시지 하나뿐이어도 DTO를 만든다. {@code Map}이나 문자열을 그냥 내보내면
 * 나중에 필드를 하나 추가할 때 응답 구조가 조용히 달라져 프론트가 깨진다.
 */
public record LogoutResponse(String message) {

    private static final String COMPLETED = "로그아웃 완료";

    public static LogoutResponse completed() {
        return new LogoutResponse(COMPLETED);
    }
}
