package com.lottotrip.auth.oauth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;

/**
 * 소셜 토큰 검증의 공통 뼈대. (roadmap 4-3-2, tour_api_erd.md 2-3)
 *
 * provider마다 검증 방식이 다르다.
 *   - 카카오 — access token으로 카카오 사용자 정보 API를 호출한다 (외부 HTTP)
 *   - 구글·애플 — identity token(JWT)을 공개키(JWKS)로 서명 검증한다 (계산)
 *
 * 하지만 **앞뒤로 붙는 검사는 셋 다 같다.** "토큰이 비었나" → 검증 → "사용자 식별자가 나왔나".
 * 이 공통 순서를 부모가 {@link #verify(String)}에 고정하고, 가운데 한 칸({@link #doVerify})만
 * 자식이 채우게 하는 방식을 **템플릿 메서드 패턴**이라 한다.
 *
 * 이렇게 하는 이유는 두 가지다.
 *   1. 같은 검사를 세 곳에 복사하지 않아도 된다. 나중에 검사를 하나 추가할 때 한 곳만 고치면 된다.
 *   2. 자식이 검사를 **빠뜨릴 수 없다.** `verify`가 `final`이라 자식이 덮어쓸 수 없기 때문이다.
 */
public abstract class SocialTokenVerifier {

    /**
     * 소셜 토큰을 검증하고 사용자 정보를 돌려준다.
     *
     * `final`을 붙여 자식이 이 흐름 자체를 바꾸지 못하게 막는다. 자식이 여기를 덮어쓰면
     * 사전·사후 검사를 건너뛴 구현이 섞여 들어올 수 있고, 그러면 "모든 provider가 같은 검사를 거친다"는
     * 보장이 깨진다.
     */
    public final OAuthUserInfo verify(String token) {
        validateNotBlank(token);
        OAuthUserInfo info = doVerify(token);
        validateUserInfo(info);
        return info;
    }

    /**
     * provider별 실제 검증 로직. 자식이 구현한다.
     *
     * `protected`라서 바깥에서는 직접 호출할 수 없다. 검사를 건너뛴 채 이 메서드만
     * 부르는 길을 막아 두는 것이다. 반드시 {@link #verify(String)}를 거쳐야 한다.
     */
    protected abstract OAuthUserInfo doVerify(String token);

    /** 이 구현체가 담당하는 provider. `AuthService`가 구현체를 고를 때 쓴다. */
    public abstract ProviderType getType();

    /**
     * 빈 토큰은 외부 호출 전에 걸러낸다.
     *
     * 어차피 실패할 요청을 카카오·구글 서버까지 보내면 응답이 느려지고 쓸데없는 트래픽이 된다.
     */
    private void validateNotBlank(String token) {
        if (token == null || token.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    /**
     * 검증에 성공했더라도 사용자 식별자가 없으면 쓸 수 없다.
     *
     * `providerUserId`는 로그인이 사람을 찾는 유일한 기준이다(이메일로 찾지 않는다).
     * 이것이 비어 있으면 회원을 조회할 수도, 새로 만들 수도 없다. 그대로 진행하면
     * `provider_user_id`가 빈 문자열인 유령 회원이 생긴다.
     */
    private void validateUserInfo(OAuthUserInfo info) {
        if (info == null || info.providerUserId() == null || info.providerUserId().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }
}
