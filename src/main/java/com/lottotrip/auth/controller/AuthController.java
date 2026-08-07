package com.lottotrip.auth.controller;

import com.lottotrip.auth.dto.LoginRequest;
import com.lottotrip.auth.dto.LoginResponse;
import com.lottotrip.auth.service.AuthService;
import com.lottotrip.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. (tour_api_erd.md 4-1)
 *
 * <p>컨트롤러는 <b>받아서 넘기고 결과를 감싸는 일</b>만 한다. 판단과 처리는 서비스가 한다.
 * 이렇게 나눠 두면 로그인 로직을 테스트할 때 HTTP를 흉내 낼 필요가 없다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 소셜 로그인. 인증이 필요 없는 경로다(로그인해야 토큰이 생기므로).
     *
     * <p>{@code @Valid}가 붙어 있으면 {@link LoginRequest}의 검사 규칙이 <b>이 메서드에 들어오기 전에</b>
     * 확인된다. 어긋나면 스프링이 예외를 던지고 {@code GlobalExceptionHandler}가 400으로 바꾼다.
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
