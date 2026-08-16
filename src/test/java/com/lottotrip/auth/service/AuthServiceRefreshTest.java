package com.lottotrip.auth.service;

import com.lottotrip.auth.dto.RefreshRequest;
import com.lottotrip.auth.dto.RefreshResponse;
import com.lottotrip.auth.jwt.JwtProperties;
import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 토큰 갱신 검증. (roadmap 4-6, tour_api_erd.md 4-1)
 *
 * 액세스 토큰은 수명이 짧다(1시간). 만료될 때마다 다시 소셜 로그인을 시키면 사용자가 불편하므로,
 * 수명이 긴 리프레시 토큰(2주)으로 **새 액세스 토큰만** 받아 간다.
 *
 * 이 서비스의 리프레시 토큰은 **서버에 저장하지 않는다(stateless).** 서명이 맞고 만료되지 않았으면
 * 유효한 것으로 본다. 그래서 별도 저장소 없이 검증만으로 처리된다.
 */
class AuthServiceRefreshTest {

    private static final Long USER_ID = 42L;

    private UserRepository userRepository;
    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtProvider = new JwtProvider(new JwtProperties(
                "test-only-secret-key-for-auth-service-32bytes-over", 3600L, 1_209_600L));
        authService = new AuthService(List.of(), userRepository, mock(SocialAuthRepository.class), jwtProvider);
        given(userRepository.existsById(any())).willReturn(true);
    }

    private String validRefreshToken() {
        return jwtProvider.createRefreshToken(USER_ID);
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("유효한 리프레시 토큰이면 새 액세스 토큰을 발급한다")
    void issuesNewAccessToken() {
        RefreshResponse response = authService.refresh(new RefreshRequest(validRefreshToken()));

        assertThat(jwtProvider.getUserIdFromAccessToken(response.accessToken())).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("발급된 토큰은 액세스 용도라 리프레시로는 쓸 수 없다")
    void issuedTokenIsAccessTypeOnly() {
        // 용도를 섞으면 수명 1시간짜리 토큰으로 2주 동안 갱신을 반복할 수 있게 된다.
        RefreshResponse response = authService.refresh(new RefreshRequest(validRefreshToken()));

        assertThatThrownBy(() -> jwtProvider.getUserIdFromRefreshToken(response.accessToken()))
                .isInstanceOf(CustomException.class);
    }

    // ---------- 실패 ----------

    @Test
    @DisplayName("액세스 토큰을 보내면 INVALID_REFRESH_TOKEN")
    void rejectsAccessToken() {
        // 두 토큰은 형태가 같고 둘 다 우리 키로 서명한 진짜다. type 클레임으로만 구분된다. (4-1)
        String accessToken = jwtProvider.createAccessToken(USER_ID);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(accessToken)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰이면 INVALID_REFRESH_TOKEN")
    void rejectsForgedToken() {
        JwtProvider attacker = new JwtProvider(new JwtProperties(
                "attacker-secret-key-that-is-long-enough-32bytes", 3600L, 1_209_600L));
        String forged = attacker.createRefreshToken(USER_ID);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(forged)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("만료된 리프레시 토큰이면 INVALID_REFRESH_TOKEN")
    void rejectsExpiredToken() {
        // 유효기간을 음수로 준 발급기로 만료를 재현한다. 2주를 기다리지 않는다. (4-1과 같은 방식)
        JwtProvider expiredIssuer = new JwtProvider(new JwtProperties(
                "test-only-secret-key-for-auth-service-32bytes-over", 3600L, -1L));
        String expired = expiredIssuer.createRefreshToken(USER_ID);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(expired)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-jwt"})
    @DisplayName("토큰 형식이 아니면 INVALID_REFRESH_TOKEN")
    void rejectsMalformedToken(String token) {
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(token)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("탈퇴 등으로 회원이 없으면 INVALID_REFRESH_TOKEN")
    void rejectsWhenUserGone() {
        // 서명만 보고 발급하면, 사라진 회원의 토큰으로 계속 새 액세스 토큰이 나온다.
        // 저장하지 않는(stateless) 구조라 토큰 자체를 무효화할 수 없으므로 여기서 회원 존재를 확인한다.
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(validRefreshToken())))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
