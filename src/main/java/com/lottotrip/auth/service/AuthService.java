package com.lottotrip.auth.service;

import com.lottotrip.auth.dto.LoginRequest;
import com.lottotrip.auth.dto.LoginResponse;
import com.lottotrip.auth.dto.LogoutResponse;
import com.lottotrip.auth.dto.RefreshRequest;
import com.lottotrip.auth.dto.RefreshResponse;
import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.entity.SocialAuth;
import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.auth.oauth.OAuthUserInfo;
import com.lottotrip.auth.oauth.SocialTokenVerifier;
import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 인증 서비스. (roadmap 4-3-3, 4-5~4-7)
 *
 * provider별 검증 구현체를 고르고, 로그인·토큰 갱신·로그아웃을 처리한다.
 */
@Slf4j
@Service
public class AuthService {

    private final Map<ProviderType, SocialTokenVerifier> verifiers;
    private final UserRepository userRepository;
    private final SocialAuthRepository socialAuthRepository;
    private final JwtProvider jwtProvider;

    /**스프링이 `SocialTokenVerifier`를 상속한 빈을 전부 모아 리스트로 넣어 준다.*/
    public AuthService(List<SocialTokenVerifier> verifierList,
                       UserRepository userRepository,
                       SocialAuthRepository socialAuthRepository,
                       JwtProvider jwtProvider) {
        this.verifiers = toVerifierMap(verifierList);
        this.userRepository = userRepository;
        this.socialAuthRepository = socialAuthRepository;
        this.jwtProvider = jwtProvider;
        log.info("소셜 로그인 구현체 {}개 등록: {}", verifiers.size(), verifiers.keySet());
    }

    /**
     * 스프링이 주는 리스트(SocialTokenVerifier을 상속한 빈)를 provider → 구현체 map으로 바꾼다.
     *
     * 매 요청마다 리스트를 훑어 찾을 수도 있지만, map으로 한 번 만들어 두면 바로 꺼낼 수 있다.
     * `EnumMap`은 키가 enum일 때 쓰는 map으로, 내부적으로 배열을 써서 일반 `HashMap`보다 가볍다.
     *
     * 같은 provider 구현체가 둘이면 여기서 즉시 실패시킨다. 조용히 덮어쓰면 둘 중 어느 것이
     * 쓰이는지 알 수 없고, 그 사실은 운영 중 로그인 실패로만 드러난다. 이 클래스는 서버가 뜰 때
     * 만들어지므로 잘못된 상태는 기동 시점에 드러나는 편이 낫다.
     */
    private Map<ProviderType, SocialTokenVerifier> toVerifierMap(List<SocialTokenVerifier> verifierList) {
        Map<ProviderType, SocialTokenVerifier> map = new EnumMap<>(ProviderType.class);
        for (SocialTokenVerifier verifier : verifierList) {
            SocialTokenVerifier previous = map.put(verifier.getType(), verifier);
            if (previous != null) {
                throw new IllegalStateException(
                        "%s를 담당하는 SocialTokenVerifier가 둘입니다: %s, %s".formatted(
                                verifier.getType(),
                                previous.getClass().getSimpleName(),
                                verifier.getClass().getSimpleName()));
            }
        }
        return map;
    }

    /**
     * provider에 맞는 구현체를 골라 소셜 토큰을 검증한다.
     *
     * 지원하지 않는 provider면 {@link ErrorCode#BAD_REQUEST}다. 구현체를 못 찾은 채 그대로
     * 진행하면 `NullPointerException`이나 500으로 떨어지는데, 그것은 서버 잘못이 아니라
     * 요청이 잘못된 것이므로 400으로 정의한다. (tour_api_erd.md 4-1 로그인)
     */
    public OAuthUserInfo verifySocialToken(ProviderType provider, String providerToken) {
        SocialTokenVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            log.debug("지원하지 않는 provider 요청: {}", provider);
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        return verifier.verify(providerToken);
    }

    /**
     * 소셜 로그인. 없으면 가입시키고, 있으면 그대로 로그인시킨다. (tour_api_erd.md 4-1)
     *
     * 사람을 찾는 기준은 `provider` + `providerUserId`다.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        ProviderType provider = ProviderType.from(request.provider());
        OAuthUserInfo userInfo = verifySocialToken(provider, request.providerToken());

        Optional<SocialAuth> existing =
                socialAuthRepository.findByProviderAndProviderUserId(provider, userInfo.providerUserId());

        boolean isNewUser = existing.isEmpty();
        User user = existing
                .map(socialAuth -> loginExisting(socialAuth, request.providerToken()))
                .orElseGet(() -> signUp(provider, userInfo, request.providerToken()));

        return new LoginResponse(
                jwtProvider.createAccessToken(user.getId()),
                jwtProvider.createRefreshToken(user.getId()),
                new LoginResponse.UserInfo(user.getId(), user.getNickname(), isNewUser));
    }

    /**
     * 액세스 토큰 갱신. (tour_api_erd.md 4-1)
     *
     * 액세스 토큰은 수명이 짧다(1시간). 만료될 때마다 소셜 로그인을 다시 시키면 사용자가 불편하므로,
     * 수명이 긴 리프레시 토큰(2주)으로 새 액세스 토큰만 받아 간다.
     */
    @Transactional(readOnly = true)
    public RefreshResponse refresh(RefreshRequest request) {
        Long userId = jwtProvider.getUserIdFromRefreshToken(request.refreshToken());

        // 토큰이 진짜여도 그 회원이 아직 있는지는 별개다. 탈퇴한 회원의 토큰으로 계속 새
        // 액세스 토큰이 나오면 안 된다. 저장하지 않는 구조라 토큰 자체를 막을 수 없으므로 여기서 본다.
        if (!userRepository.existsById(userId)) {
            log.debug("존재하지 않는 회원의 갱신 요청: userId={}", userId);
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        return new RefreshResponse(jwtProvider.createAccessToken(userId));
    }

    /**로그아웃. (tour_api_erd.md 4-1)*/
    public LogoutResponse logout(Long userId) {
        log.info("로그아웃: userId={}", userId);
        return LogoutResponse.completed();
    }

    /**
     * 신규 가입 — 회원과 소셜 계정을 함께 만든다.
     * 이름·이메일을 **반드시 이 시점에 저장.
     */
    private User signUp(ProviderType provider, OAuthUserInfo userInfo, String providerToken) {
        User user = userRepository.save(
                User.create(userInfo.email(), userInfo.nickname(), userInfo.profileImageUrl()));

        socialAuthRepository.save(SocialAuth.create(
                user, provider, userInfo.providerUserId(), providerToken, null));

        log.info("신규 가입: userId={}, provider={}", user.getId(), provider);
        return user;
    }

    /**기존 로그인 — 소셜 토큰만 새 값으로 바꾼다.*/
    private User loginExisting(SocialAuth socialAuth, String providerToken) {
        socialAuth.updateTokens(providerToken, socialAuth.getRefreshToken());
        return socialAuth.getUser();
    }
}
