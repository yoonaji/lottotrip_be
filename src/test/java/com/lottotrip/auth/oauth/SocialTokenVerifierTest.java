package com.lottotrip.auth.oauth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 소셜 토큰 검증 공통 흐름 검증. (roadmap 4-3-2)
 *
 * <p>provider마다 검증 방식이 다르지만(카카오=API 호출, 구글·애플=JWT 서명 검증)
 * <b>앞뒤로 붙는 검사는 똑같다.</b> 그 공통 부분을 부모가 고정하고 자식은 가운데만 채우는
 * 구조가 템플릿 메서드 패턴이다. (tour_api_erd.md 2-3)
 *
 * <p>여기서는 부모의 흐름만 검증한다. 실제 provider 구현체는 4-4에서 붙는다.
 * 그래서 테스트 안에 <b>가짜 자식</b>을 만들어 "부모가 자식을 언제 부르는지"를 본다.
 */
class SocialTokenVerifierTest {

    /**
     * 테스트용 가짜 구현체.
     *
     * <p>doVerify가 몇 번 불렸는지 세어 둔다. 부모가 <b>사전 검사에서 걸러낸 요청을
     * 자식에게 넘기지 않는지</b>를 확인하려면 호출 여부를 알아야 하기 때문이다.
     */
    private static class FakeVerifier extends SocialTokenVerifier {

        private final OAuthUserInfo result;
        private final AtomicInteger doVerifyCallCount = new AtomicInteger();

        FakeVerifier(OAuthUserInfo result) {
            this.result = result;
        }

        @Override
        protected OAuthUserInfo doVerify(String token) {
            doVerifyCallCount.incrementAndGet();
            return result;
        }

        @Override
        public ProviderType getType() {
            return ProviderType.KAKAO;
        }
    }

    private static final OAuthUserInfo VALID_INFO =
            new OAuthUserInfo("kakao-12345", "potato@example.com", "감자러버", "https://img.example.com/1.png");

    // ---------- 성공 ----------

    @Test
    @DisplayName("정상 토큰이면 자식이 만든 사용자 정보를 그대로 돌려준다")
    void returnsUserInfoOnValidToken() {
        FakeVerifier verifier = new FakeVerifier(VALID_INFO);

        OAuthUserInfo info = verifier.verify("valid-token");

        assertThat(info).isEqualTo(VALID_INFO);
        assertThat(verifier.doVerifyCallCount).hasValue(1);
    }

    @Test
    @DisplayName("이메일·닉네임이 없어도 providerUserId만 있으면 통과한다")
    void allowsMissingEmailAndNickname() {
        // 애플에서 이메일 가리기를 선택하면 이메일이 오지 않는다. 이것은 정상 상황이다.
        OAuthUserInfo minimal = new OAuthUserInfo("apple-abc", null, null, null);

        OAuthUserInfo info = new FakeVerifier(minimal).verify("valid-token");

        assertThat(info.providerUserId()).isEqualTo("apple-abc");
        assertThat(info.email()).isNull();
    }

    // ---------- 토큰 사전 검사 ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("토큰이 비어 있으면 INVALID_PROVIDER_TOKEN")
    void rejectsBlankToken(String blank) {
        assertThatThrownBy(() -> new FakeVerifier(VALID_INFO).verify(blank))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("토큰이 비어 있으면 자식의 doVerify를 아예 호출하지 않는다")
    void doesNotCallChildWhenTokenIsBlank() {
        FakeVerifier verifier = new FakeVerifier(VALID_INFO);

        assertThatThrownBy(() -> verifier.verify(""))
                .isInstanceOf(CustomException.class);

        // 명백히 잘못된 요청을 카카오·구글 서버까지 보내면 불필요한 외부 호출이 된다.
        assertThat(verifier.doVerifyCallCount).hasValue(0);
    }

    // ---------- 결과 사후 검사 ----------

    @Test
    @DisplayName("자식이 null을 돌려주면 INVALID_PROVIDER_TOKEN")
    void rejectsNullUserInfo() {
        assertThatThrownBy(() -> new FakeVerifier(null).verify("valid-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("providerUserId가 없으면 INVALID_PROVIDER_TOKEN")
    void rejectsMissingProviderUserId(String blank) {
        OAuthUserInfo broken = new OAuthUserInfo(blank, "potato@example.com", "감자러버", null);

        assertThatThrownBy(() -> new FakeVerifier(broken).verify("valid-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    // ---------- 구현체 식별 ----------

    @Test
    @DisplayName("구현체는 자신이 담당하는 provider를 밝힌다")
    void exposesItsProviderType() {
        assertThat(new FakeVerifier(VALID_INFO).getType()).isEqualTo(ProviderType.KAKAO);
    }
}
