package com.lottotrip.auth.service;

import com.lottotrip.auth.dto.LoginRequest;
import com.lottotrip.auth.dto.LoginResponse;
import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.entity.SocialAuth;
import com.lottotrip.auth.jwt.JwtProperties;
import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.auth.oauth.OAuthUserInfo;
import com.lottotrip.auth.oauth.SocialTokenVerifier;
import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 로그인 처리 검증. (roadmap 4-5)
 *
 * <p>DB를 띄우지 않고 저장소를 가짜(mock)로 끼운다. 여기서 확인할 것은 "무엇이 저장되는가"가 아니라
 * <b>"신규 가입과 기존 로그인을 올바로 갈라내는가"</b>이기 때문이다. 실제 저장 동작은 3-8의
 * Repository 테스트가 이미 검증했다.
 *
 * <p>JWT는 진짜를 쓴다. 가짜로 만들면 "발급된 토큰에서 userId가 나오는가"를 확인할 수 없다.
 */
class AuthServiceLoginTest {

    private static final String KAKAO_TOKEN = "kakao-access-token";
    private static final Long USER_ID = 42L;

    private static final OAuthUserInfo KAKAO_USER = new OAuthUserInfo(
            "kakao-12345", "potato@example.com", "감자러버", "https://img.kakao.com/potato.png");

    private UserRepository userRepository;
    private SocialAuthRepository socialAuthRepository;
    private JwtProvider jwtProvider;
    private AuthService authService;

    /** 지정한 사용자 정보를 돌려주는 가짜 검증기. 실제 카카오를 부르지 않는다. */
    private static class FakeVerifier extends SocialTokenVerifier {
        private final ProviderType type;
        private final OAuthUserInfo result;

        FakeVerifier(ProviderType type, OAuthUserInfo result) {
            this.type = type;
            this.result = result;
        }

        @Override
        protected OAuthUserInfo doVerify(String token) {
            return result;
        }

        @Override
        public ProviderType getType() {
            return type;
        }
    }

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        socialAuthRepository = mock(SocialAuthRepository.class);
        jwtProvider = new JwtProvider(new JwtProperties(
                "test-only-secret-key-for-auth-service-32bytes-over", 3600L, 1_209_600L));
        authService = newService(new FakeVerifier(ProviderType.KAKAO, KAKAO_USER));
    }

    private AuthService newService(SocialTokenVerifier... verifiers) {
        return new AuthService(List.of(verifiers), userRepository, socialAuthRepository, jwtProvider);
    }

    /** DB가 매겨 주는 id를 테스트에서 흉내 낸다. 실제로는 저장 시점에 채워진다. */
    private User userWithId(Long id, String nickname) {
        User user = User.create("potato@example.com", nickname, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void givenNewUser() {
        given(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.KAKAO, "kakao-12345"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", USER_ID);
                    return saved;
                });
    }

    private SocialAuth givenExistingUser() {
        User user = userWithId(USER_ID, "감자러버");
        SocialAuth socialAuth = SocialAuth.create(
                user, ProviderType.KAKAO, "kakao-12345", "old-access-token", "old-refresh-token");
        given(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.KAKAO, "kakao-12345"))
                .willReturn(Optional.of(socialAuth));
        return socialAuth;
    }

    // ---------- 신규 가입 ----------

    @Test
    @DisplayName("처음 로그인하면 회원을 만들고 isNewUser=true로 응답한다")
    void createsUserOnFirstLogin() {
        givenNewUser();

        LoginResponse response = authService.login(new LoginRequest("kakao", KAKAO_TOKEN));

        assertThat(response.user().userId()).isEqualTo(USER_ID);
        assertThat(response.user().nickname()).isEqualTo("감자러버");
        assertThat(response.user().isNewUser()).isTrue();
    }

    @Test
    @DisplayName("신규 가입 시 이름·이메일을 저장한다")
    void savesProfileOnSignUp() {
        // 애플·구글은 이름·이메일을 최초 1회만 준다. 이때 저장하지 않으면 영영 받을 수 없다.
        // (tour_api_erd.md 결정 4)
        givenNewUser();

        authService.login(new LoginRequest("kakao", KAKAO_TOKEN));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("potato@example.com");
        assertThat(captor.getValue().getNickname()).isEqualTo("감자러버");
        assertThat(captor.getValue().getProfileImageUrl()).isEqualTo("https://img.kakao.com/potato.png");
    }

    @Test
    @DisplayName("신규 가입 시 소셜 계정을 함께 저장한다")
    void savesSocialAuthOnSignUp() {
        givenNewUser();

        authService.login(new LoginRequest("kakao", KAKAO_TOKEN));

        ArgumentCaptor<SocialAuth> captor = ArgumentCaptor.forClass(SocialAuth.class);
        verify(socialAuthRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo(ProviderType.KAKAO);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("kakao-12345");
        assertThat(captor.getValue().getAccessToken()).isEqualTo(KAKAO_TOKEN);
    }

    @Test
    @DisplayName("이메일이 없어도 가입된다")
    void allowsSignUpWithoutEmail() {
        // 애플에서 이메일 가리기(Private Relay)를 선택하면 이메일이 오지 않는다.
        OAuthUserInfo noEmail = new OAuthUserInfo("apple-abc", null, null, null);
        authService = newService(new FakeVerifier(ProviderType.APPLE, noEmail));
        given(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.APPLE, "apple-abc"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", USER_ID);
                    return saved;
                });

        LoginResponse response = authService.login(new LoginRequest("apple", "identity-token"));

        assertThat(response.user().isNewUser()).isTrue();
        assertThat(response.user().nickname()).isNull();
    }

    // ---------- 기존 로그인 ----------

    @Test
    @DisplayName("이미 가입한 회원이면 회원을 새로 만들지 않고 isNewUser=false로 응답한다")
    void reusesExistingUser() {
        givenExistingUser();

        LoginResponse response = authService.login(new LoginRequest("kakao", KAKAO_TOKEN));

        assertThat(response.user().userId()).isEqualTo(USER_ID);
        assertThat(response.user().isNewUser()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("기존 로그인이면 소셜 토큰을 새 값으로 갱신한다")
    void updatesSocialTokenOnExistingLogin() {
        SocialAuth socialAuth = givenExistingUser();

        authService.login(new LoginRequest("kakao", KAKAO_TOKEN));

        // 영속성 컨텍스트가 변경을 감지해 UPDATE를 날리므로 save를 부르지 않아도 된다.
        assertThat(socialAuth.getAccessToken()).isEqualTo(KAKAO_TOKEN);
    }

    // ---------- 토큰 발급 ----------

    @Test
    @DisplayName("발급한 액세스·리프레시 토큰에서 userId를 꺼낼 수 있다")
    void issuesUsableTokens() {
        givenExistingUser();

        LoginResponse response = authService.login(new LoginRequest("kakao", KAKAO_TOKEN));

        assertThat(jwtProvider.getUserIdFromAccessToken(response.accessToken())).isEqualTo(USER_ID);
        assertThat(jwtProvider.getUserIdFromRefreshToken(response.refreshToken())).isEqualTo(USER_ID);
    }

    // ---------- provider 값 처리 ----------

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "KAKAO", "Kakao"})
    @DisplayName("provider는 대소문자를 가리지 않는다")
    void acceptsProviderIgnoringCase(String provider) {
        givenExistingUser();

        assertThat(authService.login(new LoginRequest(provider, KAKAO_TOKEN)).user().userId())
                .isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("정의되지 않은 provider면 BAD_REQUEST")
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("naver", KAKAO_TOKEN)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("구현체가 없는 provider면 BAD_REQUEST")
    void rejectsUnsupportedProvider() {
        // 애플은 개발자 계정 미보유로 아직 구현체가 없다. (roadmap 4-4-3)
        assertThatThrownBy(() -> authService.login(new LoginRequest("apple", "identity-token")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("소셜 토큰이 유효하지 않으면 회원을 만들지 않는다")
    void doesNotCreateUserOnInvalidToken() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("kakao", "  ")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);

        verify(userRepository, never()).save(any(User.class));
        verify(socialAuthRepository, never()).save(any(SocialAuth.class));
    }
}
