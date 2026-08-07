package com.lottotrip.auth.service;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.oauth.OAuthUserInfo;
import com.lottotrip.auth.oauth.SocialTokenVerifier;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * provider별 검증 구현체 선택 로직 검증. (roadmap 4-3-3)
 *
 * <p>스프링은 {@code List<SocialTokenVerifier>}를 생성자로 받으면 <b>그 타입의 빈을 전부 모아</b>
 * 넣어 준다. 그래서 provider가 늘어나도 이 클래스는 고치지 않아도 된다.
 * (tour_api_erd.md 2-3 — 개방·폐쇄 원칙)
 *
 * <p>여기서는 스프링을 띄우지 않고 리스트를 직접 만들어 넘긴다. 생성자 주입의 장점이
 * 바로 이것 — 테스트에서 프레임워크 없이 객체를 만들 수 있다.
 */
class AuthServiceTest {

    /** 담당 provider와 돌려줄 결과만 정해 두는 가짜 구현체. */
    private static class FakeVerifier extends SocialTokenVerifier {

        private final ProviderType type;
        private final String providerUserId;

        FakeVerifier(ProviderType type) {
            this(type, type.name().toLowerCase() + "-user-1");
        }

        FakeVerifier(ProviderType type, String providerUserId) {
            this.type = type;
            this.providerUserId = providerUserId;
        }

        @Override
        protected OAuthUserInfo doVerify(String token) {
            return new OAuthUserInfo(providerUserId, "potato@example.com", "감자러버", null);
        }

        @Override
        public ProviderType getType() {
            return type;
        }
    }

    private AuthService newService(SocialTokenVerifier... verifiers) {
        return new AuthService(List.of(verifiers));
    }

    // ---------- 선택 ----------

    @Test
    @DisplayName("provider에 맞는 구현체를 골라 검증을 위임한다")
    void delegatesToMatchingVerifier() {
        AuthService authService = newService(
                new FakeVerifier(ProviderType.KAKAO),
                new FakeVerifier(ProviderType.GOOGLE));

        OAuthUserInfo info = authService.verifySocialToken(ProviderType.GOOGLE, "token");

        assertThat(info.providerUserId()).isEqualTo("google-user-1");
    }

    @Test
    @DisplayName("등록된 provider가 여러 개여도 서로 섞이지 않는다")
    void keepsVerifiersSeparated() {
        AuthService authService = newService(
                new FakeVerifier(ProviderType.KAKAO),
                new FakeVerifier(ProviderType.GOOGLE),
                new FakeVerifier(ProviderType.APPLE));

        assertThat(authService.verifySocialToken(ProviderType.KAKAO, "t").providerUserId())
                .isEqualTo("kakao-user-1");
        assertThat(authService.verifySocialToken(ProviderType.APPLE, "t").providerUserId())
                .isEqualTo("apple-user-1");
    }

    @Test
    @DisplayName("지원하지 않는 provider면 BAD_REQUEST")
    void rejectsUnsupportedProvider() {
        // 애플은 개발자 계정 미보유로 4-4-3 보류 상태다. 구현체가 없는 provider로 요청이 오면
        // 500(NPE)이 아니라 400으로 명확히 거절해야 한다.
        AuthService authService = newService(new FakeVerifier(ProviderType.KAKAO));

        assertThatThrownBy(() -> authService.verifySocialToken(ProviderType.APPLE, "token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("provider가 null이면 BAD_REQUEST")
    void rejectsNullProvider() {
        AuthService authService = newService(new FakeVerifier(ProviderType.KAKAO));

        assertThatThrownBy(() -> authService.verifySocialToken(null, "token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    // ---------- 위임 이후 ----------

    @Test
    @DisplayName("구현체가 던진 INVALID_PROVIDER_TOKEN을 그대로 전달한다")
    void propagatesVerificationFailure() {
        // 부모(SocialTokenVerifier)의 빈 토큰 검사가 그대로 살아 있어야 한다.
        AuthService authService = newService(new FakeVerifier(ProviderType.KAKAO));

        assertThatThrownBy(() -> authService.verifySocialToken(ProviderType.KAKAO, "  "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    // ---------- 등록 자체의 오류 ----------

    @Test
    @DisplayName("같은 provider 구현체가 둘이면 생성 시점에 실패한다")
    void failsFastOnDuplicateProvider() {
        // 서버가 뜨는 순간 터져야 한다. 그냥 넘어가면 둘 중 어느 것이 쓰일지 알 수 없고,
        // 그 사실은 운영 중 로그인 실패로만 드러난다.
        assertThatThrownBy(() -> newService(
                new FakeVerifier(ProviderType.KAKAO, "first"),
                new FakeVerifier(ProviderType.KAKAO, "second")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO");
    }

    @Test
    @DisplayName("구현체가 하나도 없어도 생성 자체는 된다")
    void allowsEmptyVerifierList() {
        // 애플만 남기고 전부 빠지는 일은 없겠지만, 빈 목록으로도 컨텍스트는 떠야 한다.
        // 실제 요청이 왔을 때 BAD_REQUEST로 거절하면 충분하다.
        AuthService authService = newService();

        assertThatThrownBy(() -> authService.verifySocialToken(ProviderType.KAKAO, "token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
