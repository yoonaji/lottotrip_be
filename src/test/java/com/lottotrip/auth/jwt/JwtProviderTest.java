package com.lottotrip.auth.jwt;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 발급·검증·클레임 추출 검증. (roadmap 4-1)
 *
 * 액세스 토큰과 리프레시 토큰은 형태는 같지만 **거절될 때의 에러 코드가 다르다.**
 * 명세상 액세스 토큰이 없거나 만료면 `UNAUTHORIZED`, 리프레시 토큰이 잘못되면
 * `INVALID_REFRESH_TOKEN`이다. (tour_api_erd.md 4-1)
 * 그래서 추출 메서드를 용도별로 따로 둔다.
 */
class JwtProviderTest {

    /** HS256은 256비트(32바이트) 이상의 키를 요구한다. 테스트용 더미 값. */
    private static final String SECRET = "test-only-secret-key-for-jwt-provider-32bytes-over";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-long-enough-32bytes";

    private static final long ACCESS_VALIDITY = 3600L;
    private static final long REFRESH_VALIDITY = 1_209_600L;

    private final JwtProvider jwtProvider = newProvider(SECRET, ACCESS_VALIDITY, REFRESH_VALIDITY);

    private JwtProvider newProvider(String secret, long accessSeconds, long refreshSeconds) {
        return new JwtProvider(new JwtProperties(secret, accessSeconds, refreshSeconds));
    }

    // ---------- 발급 & 추출 ----------

    @Test
    @DisplayName("액세스 토큰을 발급하고 다시 userId를 꺼낼 수 있다")
    void issuesAndReadsAccessToken() {
        String token = jwtProvider.createAccessToken(42L);

        assertThat(jwtProvider.getUserIdFromAccessToken(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("리프레시 토큰을 발급하고 다시 userId를 꺼낼 수 있다")
    void issuesAndReadsRefreshToken() {
        String token = jwtProvider.createRefreshToken(42L);

        assertThat(jwtProvider.getUserIdFromRefreshToken(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("같은 사용자라도 액세스 토큰과 리프레시 토큰은 서로 다른 값이다")
    void accessAndRefreshTokensDiffer() {
        assertThat(jwtProvider.createAccessToken(1L))
                .isNotEqualTo(jwtProvider.createRefreshToken(1L));
    }

    @Test
    @DisplayName("토큰은 점(.)으로 구분된 JWT 3부분 구조를 가진다")
    void tokenHasJwtStructure() {
        assertThat(jwtProvider.createAccessToken(1L)).matches("^[\\w-]+\\.[\\w-]+\\.[\\w-]+$");
    }

    // ---------- 유효기간 ----------

    @Test
    @DisplayName("토큰의 유효기간은 설정값을 그대로 따른다")
    void expirationFollowsConfiguredValidity() {
        // 설정을 바꾸면 토큰 수명이 실제로 바뀌는지 본다.
        // 상수를 코드에 박아두면 운영 중 수명 조정이 불가능해지므로 설정에서 읽어야 한다.
        Claims access = parse(jwtProvider.createAccessToken(1L));
        Claims refresh = parse(jwtProvider.createRefreshToken(1L));

        assertThat(secondsBetweenIssueAndExpiry(access)).isEqualTo(ACCESS_VALIDITY);
        assertThat(secondsBetweenIssueAndExpiry(refresh)).isEqualTo(REFRESH_VALIDITY);
    }

    @Test
    @DisplayName("만료된 액세스 토큰은 UNAUTHORIZED로 거절한다")
    void rejectsExpiredAccessToken() {
        // 유효기간을 음수로 준 발급기로 "이미 만료된 토큰"을 만든다.
        // 테스트에서 실제로 기다리지 않고 만료 상황을 재현하기 위한 방법이다.
        String expired = newProvider(SECRET, -60L, -60L).createAccessToken(1L);

        assertThatCustomException(() -> jwtProvider.getUserIdFromAccessToken(expired))
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("만료된 리프레시 토큰은 INVALID_REFRESH_TOKEN으로 거절한다")
    void rejectsExpiredRefreshToken() {
        String expired = newProvider(SECRET, -60L, -60L).createRefreshToken(1L);

        assertThatCustomException(() -> jwtProvider.getUserIdFromRefreshToken(expired))
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ---------- 위조 · 손상 ----------

    @Test
    @DisplayName("다른 비밀키로 서명된 토큰은 거절한다")
    void rejectsTokenSignedWithAnotherKey() {
        // 서명 검증의 핵심. 이게 뚫리면 누구나 아무 userId로 토큰을 만들어 남의 계정에 접근할 수 있다.
        String forged = newProvider(OTHER_SECRET, ACCESS_VALIDITY, REFRESH_VALIDITY).createAccessToken(999L);

        assertThatCustomException(() -> jwtProvider.getUserIdFromAccessToken(forged))
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-jwt", "aaa.bbb.ccc", "eyJhbGciOiJIUzI1NiJ9", "   "})
    @DisplayName("형식이 깨진 토큰은 UNAUTHORIZED로 거절한다")
    void rejectsMalformedToken(String token) {
        assertThatCustomException(() -> jwtProvider.getUserIdFromAccessToken(token))
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("토큰이 없으면 UNAUTHORIZED로 거절한다")
    void rejectsMissingToken(String token) {
        assertThatCustomException(() -> jwtProvider.getUserIdFromAccessToken(token))
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("리프레시 토큰이 없으면 INVALID_REFRESH_TOKEN으로 거절한다")
    void rejectsMissingRefreshToken(String token) {
        assertThatCustomException(() -> jwtProvider.getUserIdFromRefreshToken(token))
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ---------- 용도 구분 ----------

    @Test
    @DisplayName("리프레시 토큰을 액세스 토큰 자리에 쓰면 거절한다")
    void rejectsRefreshTokenUsedAsAccessToken() {
        // 둘 다 우리 키로 서명한 진짜 토큰이라 서명 검증만으로는 구분되지 않는다.
        // 수명이 긴 리프레시 토큰이 API 호출에 그대로 쓰이면, 탈취 시 피해 기간이 길어진다.
        // 그래서 토큰 안에 용도(type)를 심어두고 대조한다.
        String refresh = jwtProvider.createRefreshToken(1L);

        assertThatCustomException(() -> jwtProvider.getUserIdFromAccessToken(refresh))
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("액세스 토큰을 리프레시 토큰 자리에 쓰면 거절한다")
    void rejectsAccessTokenUsedAsRefreshToken() {
        String access = jwtProvider.createAccessToken(1L);

        assertThatCustomException(() -> jwtProvider.getUserIdFromRefreshToken(access))
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ---------- 설정 검증 ----------

    @ParameterizedTest
    @ValueSource(strings = {"", "too-short-secret"})
    @DisplayName("비밀키가 없거나 짧으면 생성 시점에 바로 실패한다")
    void rejectsWeakSecretAtStartup(String weakSecret) {
        // 서버가 뜬 뒤 첫 로그인 요청에서 500이 나는 것보다, 뜨는 순간 실패하는 편이 낫다.
        // 잘못된 설정으로 운영에 배포되는 것을 막는다.
        assertThatThrownBy(() -> newProvider(weakSecret, ACCESS_VALIDITY, REFRESH_VALIDITY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("비밀키가 null이어도 생성 시점에 바로 실패한다")
    void rejectsNullSecretAtStartup() {
        assertThatThrownBy(() -> newProvider(null, ACCESS_VALIDITY, REFRESH_VALIDITY))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- 도우미 ----------

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private long secondsBetweenIssueAndExpiry(Claims claims) {
        return (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
    }

    private org.assertj.core.api.AbstractObjectAssert<?, ErrorCode> assertThatCustomException(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode());
    }
}
