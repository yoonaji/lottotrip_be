package com.lottotrip.auth.service;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.oauth.OAuthUserInfo;
import com.lottotrip.auth.oauth.SocialTokenVerifier;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 인증 서비스. (roadmap 4-3-3)
 *
 * <p>현재는 provider별 검증 구현체를 고르는 일만 한다.
 * 로그인·토큰 갱신·로그아웃은 4-5~4-7에서 이 클래스에 추가된다.
 */
@Slf4j
@Service
public class AuthService {

    private final Map<ProviderType, SocialTokenVerifier> verifiers;

    /**
     * 스프링이 {@code SocialTokenVerifier}를 상속한 빈을 <b>전부 모아</b> 리스트로 넣어 준다.
     *
     * <p>{@code @Component}가 붙은 구현체가 새로 생기면 자동으로 이 목록에 포함된다.
     * 그래서 provider가 늘어도 이 클래스는 고치지 않는다 —
     * "기능 추가에는 열려 있고 수정에는 닫혀 있다"는 개방·폐쇄 원칙이 이런 모양이다.
     *
     * <p>참고: 생성자가 하나뿐이면 {@code @Autowired}를 생략해도 스프링이 알아서 주입한다.
     */
    public AuthService(List<SocialTokenVerifier> verifierList) {
        this.verifiers = toVerifierMap(verifierList);
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
}
