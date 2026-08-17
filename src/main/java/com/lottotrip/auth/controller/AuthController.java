package com.lottotrip.auth.controller;

import com.lottotrip.auth.dto.LoginRequest;
import com.lottotrip.auth.dto.LoginResponse;
import com.lottotrip.auth.dto.LogoutResponse;
import com.lottotrip.auth.dto.RefreshRequest;
import com.lottotrip.auth.dto.RefreshResponse;
import com.lottotrip.auth.dto.WithdrawResponse;
import com.lottotrip.auth.service.AuthService;
import com.lottotrip.auth.service.WithdrawalService;
import com.lottotrip.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final WithdrawalService withdrawalService;

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

    /**로그아웃. 인증이 필요한 인증 API. 본문은 없음.*/
    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(authService.logout(userId));
    }

    /**
     * 회원 탈퇴. 인증 필요, 본문 없음. (roadmap 9-5, 결정 20)
     *
     * 앱스토어 심사 요건이다(Guideline 5.1.1(v)) — 앱 안에서 계정 삭제를 시작할 수 있어야 하고
     * 웹 안내나 이메일 문의로 대체할 수 없다.
     *
     * 회원 번호를 토큰에서만 꺼내는 이유는 남의 계정을 지우는 요청을 아예 만들 수 없게 하기
     * 위함이다. 경로에 회원 번호가 있으면 그 값을 바꿔 보고 싶어지는 API가 된다.
     */
    @DeleteMapping("/me")
    public ApiResponse<WithdrawResponse> withdraw(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(withdrawalService.withdraw(userId));
    }
}
