package com.lottotrip.auth.oauth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.Key;
import java.util.List;
import java.util.Set;

/**
 * 애플 identity token 검증. (roadmap 4-4-3)
 *
 * 방식은 구글과 같다. 애플 공개키(JWKS)로 서명을 검사하고 `iss`·`aud`·`exp`를 본다.
 * 카카오처럼 사용자 정보 API를 따로 부르지 않는다.
 *
 * ⚠️ 팀 ID·키 ID·`.p8`는 필요 없다. 그 셋은 서버가 애플에 토큰을 요청할 때
 * (`client_secret`을 서명해 만들 때) 쓰는 값이고, 검증에는 공개키와 번들 ID면 충분하다.
 * 4-4-3이 "계정 미보유"로 보류돼 있던 것은 이 오해 때문이었다.
 * (탈퇴 시 연동 해제까지 하려면 그때는 필요해진다.)
 *
 * 구글과 다른 점
 *   - 이름·사진이 토큰에 없다 → `nickname`·`profileImageUrl`은 항상 null.
 *     애플은 이름을 최초 인증 응답 본문에 한 번만 준다
 *   - `iss`가 하나뿐이다 (구글은 스킴 유무 두 형태)
 *   - `aud`가 클라이언트 ID가 아니라 번들 ID다
 *   - 이메일이 없거나 익명 주소일 수 있다(이메일 가리기). `users.email`이 nullable인 이유다
 *
 * ⛔ `nonce`는 검증하지 않는다. 프론트가 값을 함께 보내야 성립해 API 명세가 바뀐다.
 */
@Slf4j
@Component
public class AppleTokenVerifier extends SocialTokenVerifier {

    /** 애플은 이 형태 하나만 발급한다. 구글처럼 스킴 없는 형태는 오지 않는다. */
    private static final Set<String> VALID_ISSUERS = Set.of("https://appleid.apple.com");

    private final JwksProvider jwksProvider;
    private final List<String> audiences;

    public AppleTokenVerifier(RestClient.Builder restClientBuilder, AppleOAuthProperties properties) {
        this.jwksProvider = new JwksProvider(restClientBuilder.build(), properties.jwksUri());
        this.audiences = properties.audiences();

        if (audiences.isEmpty()) {
            log.warn("애플 번들 ID(oauth.apple.audiences)가 설정되지 않아 애플 로그인이 비활성 상태입니다.");
        }
    }

    @Override
    protected OAuthUserInfo doVerify(String token) {
        requireConfigured();

        try {
            Claims claims = Jwts.parser()
                    // 어느 공개키로 검증할지는 토큰 헤더의 kid를 봐야 알 수 있다.
                    // keyLocator는 "헤더를 보고 키를 골라 오는 방법"을 알려주는 자리다.
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            return jwksProvider.findKey(header.getKeyId());
                        }
                    })
                    .build()
                    // 여기서 서명과 만료가 함께 검사된다.
                    .parseSignedClaims(token)
                    .getPayload();

            validateIssuer(claims);
            validateAudience(claims);
            return toUserInfo(claims);

        } catch (CustomException e) {
            // 공개키를 못 받아온 경우(SERVICE_UNAVAILABLE) 등 우리가 던진 예외는 그대로 올린다.
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            // jjwt가 우리 예외를 감싸서 던졌을 수 있으므로 안을 한 번 확인한다.
            if (e.getCause() instanceof CustomException wrapped) {
                throw wrapped;
            }
            // 무엇 때문에 실패했는지는 응답으로 알려주지 않는다. 위조를 시도하는 쪽에 힌트가 된다.
            log.debug("애플 토큰 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    /**
     * 번들 ID가 없으면 로그인을 막는다.
     *
     * `aud` 검사를 건너뛰면 다른 애플 앱의 토큰으로도 로그인할 수 있다. 서명은 진짜 애플
     * 것이라 서명 검사만으로는 못 걸러낸다. 구글과 같은 판단이다.
     */
    private void requireConfigured() {
        if (audiences.isEmpty()) {
            log.warn("애플 번들 ID 미설정 상태에서 로그인 시도가 들어왔습니다.");
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateIssuer(Claims claims) {
        if (!VALID_ISSUERS.contains(claims.getIssuer())) {
            log.debug("애플 토큰 issuer 불일치: {}", claims.getIssuer());
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    /**
     * 이 토큰이 우리 앱을 위해 발급된 것인지 확인한다.
     * 공격자가 자기 앱에서 받은 진짜 토큰을 우리 서버로 보내는 것을 막는다.
     */
    private void validateAudience(Claims claims) {
        Set<String> tokenAudiences = claims.getAudience();
        if (tokenAudiences == null || tokenAudiences.stream().noneMatch(audiences::contains)) {
            log.debug("애플 토큰 audience 불일치: {}", tokenAudiences);
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    /**
     * 애플 클레임을 서비스 공통 모양으로 옮긴다.
     *
     * `sub`가 식별자다. 이메일로 사람을 식별하지 않는다(결정 4).
     * 이름·사진이 null인 것은 빠뜨린 게 아니라 애플이 주지 않기 때문이다.
     */
    private OAuthUserInfo toUserInfo(Claims claims) {
        return new OAuthUserInfo(
                claims.getSubject(),
                claims.get("email", String.class),
                null,
                null);
    }

    @Override
    public ProviderType getType() {
        return ProviderType.APPLE;
    }
}
