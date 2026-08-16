package com.lottotrip.auth.controller;

import com.lottotrip.auth.dto.LoginRequest;
import com.lottotrip.auth.dto.LoginResponse;
import com.lottotrip.auth.dto.LogoutResponse;
import com.lottotrip.auth.dto.RefreshRequest;
import com.lottotrip.auth.dto.RefreshResponse;
import com.lottotrip.auth.service.AuthService;
import com.lottotrip.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. (tour_api_erd.md 4-1)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 소셜 로그인. 인증이 필요 없는 경로다 (로그인해야 토큰이 생기므로).
     *
     * `@Valid`가 붙어 있으면 {@link LoginRequest}의 검사 규칙이 이 메서드에 들어오기 전에
     * 확인된다. 어긋나면 스프링이 예외를 던지고 `GlobalExceptionHandler`가 400으로 바꾼다.
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 액세스 토큰 갱신. 이 경로도 인증이 필요 없다.
     *
     * 액세스 토큰이 만료돼서 부르는 API. 신분 확인은 본문의 리프레시 토큰이 대신한다.
     */
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    /**로그아웃. 인증이 필요한 유일한 인증 API. 본문은 없음.*/
    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(authService.logout(userId));
    }
}
