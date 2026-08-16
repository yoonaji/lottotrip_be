package com.lottotrip.auth.dto;

/**
 * 로그아웃 응답. (tour_api_erd.md 4-1)
 */
public record LogoutResponse(String message) {

    private static final String COMPLETED = "로그아웃 완료";

    public static LogoutResponse completed() {
        return new LogoutResponse(COMPLETED);
    }
}
