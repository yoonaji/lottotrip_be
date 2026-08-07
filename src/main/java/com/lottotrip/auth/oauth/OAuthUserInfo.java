package com.lottotrip.auth.oauth;

/**
 * 소셜 서비스에서 확인한 사용자 정보. (roadmap 4-3-1)
 *
 * <p>카카오·구글·애플이 각자 다른 모양의 응답을 주기 때문에, 서비스 계층이 그 차이를 알지 못하도록
 * <b>하나의 공통 모양으로 번역해 두는 자리</b>다. 이렇게 해 두면 provider가 늘어도
 * {@code AuthService}는 고칠 필요가 없다.
 *
 * <p>{@code record}는 "값을 담기만 하는 클래스"를 짧게 쓰는 문법이다. 필드 선언만 적으면
 * 생성자·getter·{@code equals}·{@code toString}이 자동으로 만들어진다. 다만 getter 이름이
 * {@code getEmail()}이 아니라 {@code email()}이다.
 *
 * <p>값을 바꿀 수 없다(불변). 검증을 통과한 사용자 정보가 중간에 바뀌면 다른 사람으로
 * 로그인될 수 있으므로, 애초에 못 바꾸게 막는 편이 안전하다.
 *
 * @param providerUserId 소셜 서비스가 발급한 사용자 식별자. <b>사람을 찾는 유일한 기준</b>이라 반드시 있어야 한다
 * @param email          없을 수 있다. 애플에서 이메일 가리기(Private Relay)를 선택하면 오지 않는다
 * @param nickname       없을 수 있다. 애플·구글은 최초 가입 시 1회만 제공한다
 * @param profileImageUrl 없을 수 있다. 카카오만 제공한다
 */
public record OAuthUserInfo(
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
