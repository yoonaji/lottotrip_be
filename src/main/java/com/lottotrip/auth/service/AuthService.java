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
 * <p>provider별 검증 구현체를 고르고, 로그인·토큰 갱신·로그아웃을 처리한다.
 */
@Slf4j
@Service
public class AuthService {

    private final Map<ProviderType, SocialTokenVerifier> verifiers;
    private final UserRepository userRepository;
    private final SocialAuthRepository socialAuthRepository;
    private final JwtProvider jwtProvider;

    /**
     * 스프링이 {@code SocialTokenVerifier}를 상속한 빈을 <b>전부 모아</b> 리스트로 넣어 준다.
     *
     * <p>{@code @Component}가 붙은 구현체가 새로 생기면 자동으로 이 목록에 포함된다.
     * 그래서 provider가 늘어도 이 클래스는 고치지 않는다 —
     * "기능 추가에는 열려 있고 수정에는 닫혀 있다"는 개방·폐쇄 원칙이 이런 모양이다.
     *
     * <p>참고: 생성자가 하나뿐이면 {@code @Autowired}를 생략해도 스프링이 알아서 주입한다.
     */
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
     * 리스트를 provider → 구현체 map으로 바꾼다.
     *
     * <p>매 요청마다 리스트를 훑어 찾을 수도 있지만, map으로 한 번 만들어 두면 바로 꺼낼 수 있다.
     * {@code EnumMap}은 키가 enum일 때 쓰는 map으로, 내부적으로 배열을 써서 일반 {@code HashMap}보다 가볍다.
     *
     * <p>같은 provider 구현체가 둘이면 <b>여기서 즉시 실패시킨다.</b> 조용히 덮어쓰면 둘 중 어느 것이
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
     * <p>지원하지 않는 provider면 {@link ErrorCode#BAD_REQUEST}다. 구현체를 못 찾은 채 그대로
     * 진행하면 {@code NullPointerException}이 나 500으로 떨어지는데, 그것은 서버 잘못이 아니라
     * <b>요청이 잘못된 것</b>이므로 400이 맞다. (tour_api_erd.md 4-1 로그인)
     *
     * <p>애플은 개발자 계정 미보유로 구현체가 아직 없다(roadmap 4-4-3 보류).
     * 그동안 애플로 로그인 요청이 오면 이 분기를 타고 400으로 거절된다.
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
     * <p>사람을 찾는 기준은 <b>{@code provider} + {@code providerUserId}</b>다. 이메일로 찾지 않는다.
     * 이메일은 사용자가 바꿀 수 있고, 애플에서는 아예 오지 않을 수도 있기 때문이다.
     *
     * <p>{@code @Transactional}은 "이 메서드 안의 DB 작업을 전부 성공시키거나 전부 되돌린다"는 뜻이다.
     * 신규 가입은 <b>회원 저장 + 소셜 계정 저장</b> 두 번의 INSERT인데, 둘 사이에서 실패하면
     * 소셜 계정이 없는 회원이 남는다. 그 회원은 다시는 로그인으로 찾을 수 없는 유령이 된다.
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
     * <p>액세스 토큰은 수명이 짧다(1시간). 만료될 때마다 소셜 로그인을 다시 시키면 사용자가 불편하므로,
     * 수명이 긴 리프레시 토큰(2주)으로 <b>새 액세스 토큰만</b> 받아 간다.
     *
     * <p>이 서비스의 리프레시 토큰은 <b>서버에 저장하지 않는다(stateless).</b> 서명이 맞고 만료되지
     * 않았으면 유효한 것으로 본다. 저장소가 필요 없어 단순하지만, <b>한 번 발급한 토큰을 중간에
     * 무효화할 수 없다</b>는 뜻이기도 하다. 그래서 로그아웃(4-7)은 앱이 토큰을 지우는 것에 의존한다.
     *
     * <p>리프레시 토큰 자체는 새로 발급하지 않는다. 그래야 갱신을 반복해도 원래 만료 시점이 유지된다.
     * 매번 새로 주면 사용자가 앱을 계속 쓰는 한 로그인 상태가 무한히 연장된다.
     *
     * <p>{@code readOnly = true}는 "이 트랜잭션은 읽기만 한다"는 표시다. JPA가 변경 감지를 위한
     * 준비 작업을 건너뛰어 조금 가벼워지고, 실수로 쓰기가 섞이면 드러난다.
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

    /**
     * 로그아웃. (tour_api_erd.md 4-1)
     *
     * <p><b>서버가 할 일이 거의 없다.</b> 우리 토큰은 서버에 저장하지 않기로 했으므로(stateless),
     * "이 토큰은 이제 무효"라고 표시할 곳이 없다. 이미 발급된 토큰은 만료될 때까지 그대로 유효하다.
     * 실제 로그아웃은 <b>앱이 갖고 있는 토큰을 지우는 것</b>으로 이뤄진다.
     *
     * <p>그럼에도 이 API가 필요한 이유는 두 가지다. 앱 입장에서 "로그아웃 절차를 서버가 확인해 줬다"는
     * 신호가 있어야 하고, 서버는 <b>누가 언제 로그아웃했는지를 기록</b>할 수 있다.
     *
     * <p>토큰을 진짜로 무효화하려면 발급한 토큰을 서버가 들고 있어야 한다(저장소 또는 블랙리스트).
     * 그건 ERD에 없는 테이블을 요구하므로 이번 범위에서는 하지 않는다.
     */
    public LogoutResponse logout(Long userId) {
        log.info("로그아웃: userId={}", userId);
        return LogoutResponse.completed();
    }

    /**
     * 신규 가입 — 회원과 소셜 계정을 함께 만든다.
     *
     * <p>이름·이메일을 <b>반드시 이 시점에 저장</b>해야 한다. 애플·구글은 최초 1회만 주기 때문에
     * 지금 버리면 두 번 다시 받을 수 없다. (tour_api_erd.md 결정 4)
     */
    private User signUp(ProviderType provider, OAuthUserInfo userInfo, String providerToken) {
        User user = userRepository.save(
                User.create(userInfo.email(), userInfo.nickname(), userInfo.profileImageUrl()));

        socialAuthRepository.save(SocialAuth.create(
                user, provider, userInfo.providerUserId(), providerToken, null));

        log.info("신규 가입: userId={}, provider={}", user.getId(), provider);
        return user;
    }

    /**
     * 기존 로그인 — 소셜 토큰만 새 값으로 바꾼다.
     *
     * <p>{@code save()}를 부르지 않는 이유는, 트랜잭션 안에서 조회한 Entity는 JPA가 계속 지켜보고 있다가
     * <b>값이 바뀐 것을 스스로 발견해</b> UPDATE를 날려 주기 때문이다(변경 감지).
     * 트랜잭션이 끝나는 시점에 처음 읽었던 값과 비교해 달라진 것만 반영한다.
     */
    private User loginExisting(SocialAuth socialAuth, String providerToken) {
        socialAuth.updateTokens(providerToken, socialAuth.getRefreshToken());
        return socialAuth.getUser();
    }
}
