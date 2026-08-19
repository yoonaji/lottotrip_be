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
 * 구글 identity token 검증. (roadmap 4-4-2)
 *
 * 카카오와 방식이 다르다. 구글이 주는 identity token은 JWT라서 사용자 정보가 토큰 안에
 * 이미 들어 있다. 그래서 사용자 정보 API를 부를 필요가 없고, 이 토큰이 진짜 구글이 발급한 것인지만
 * 확인하면 된다. 확인은 구글의 공개키로 서명을 검사하는 방식이다.
 *
 * 서명 검사만으로는 부족하고 세 가지를 더 본다.
 *   - `iss` — 발급한 곳이 구글이 맞는가
 *   - `aud` — 우리 앱을 위해 발급된 것이 맞는가
 *   - `exp` — 만료되지 않았는가 (jjwt가 자동으로 본다)
 */
@Slf4j
@Component
public class GoogleTokenVerifier extends SocialTokenVerifier {

    /** 구글은 https가 붙은 형태와 붙지 않은 형태를 모두 발급한다. 둘 다 정상이다. */
    private static final Set<String> VALID_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com");

    private final JwksProvider jwksProvider;
    private final List<String> audiences;

    public GoogleTokenVerifier(RestClient.Builder restClientBuilder, GoogleOAuthProperties properties) {
        this.jwksProvider = new JwksProvider(restClientBuilder.build(), properties.jwksUri());
        this.audiences = properties.audiences();

        if (audiences.isEmpty()) {
            log.warn("구글 클라이언트 ID(oauth.google.audiences)가 설정되지 않아 구글 로그인이 비활성 상태입니다.");
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
                    // 여기서 서명과 만료가 함께 검사된다. 하나라도 어긋나면 예외가 난다.
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
            // 서명 불일치·만료·형식 오류가 모두 여기로 온다.
            // 무엇 때문에 실패했는지는 응답으로 알려주지 않는다. 위조를 시도하는 쪽에 힌트가 되기 때문이다.
            log.debug("구글 토큰 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    /**
     * 클라이언트 ID가 없으면 로그인을 막는다.
     *
     * 설정이 없다고 `aud` 검사를 건너뛰면, 다른 구글 앱을 위해 발급된 토큰으로도
     * 우리 서비스에 로그인할 수 있게 된다. 서명은 진짜 구글 것이라 서명 검사만으로는 못 걸러낸다.
     * 그래서 "검사를 못 하면 아예 받지 않는" 쪽을 택한다.
     *
     * 구현체가 없는 애플이 `BAD_REQUEST`를 받는 것과 같은 취급이다. 실제로 둘 다
     * "이 provider로는 지금 로그인할 수 없다"는 상태다.
     */
    private void requireConfigured() {
        if (audiences.isEmpty()) {
            log.warn("구글 클라이언트 ID 미설정 상태에서 로그인 시도가 들어왔습니다.");
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateIssuer(Claims claims) {
        if (!VALID_ISSUERS.contains(claims.getIssuer())) {
            log.debug("구글 토큰 issuer 불일치: {}", claims.getIssuer());
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    /**
     * 이 토큰이 우리 앱을 위해 발급된 것인지 확인한다.
     *
     * 이 검사가 빠지면 이런 일이 가능하다. 공격자가 자기 앱을 하나 만들어 구글 로그인을 붙이고,
     * 거기서 받은 진짜 구글 토큰을 우리 서버로 보낸다. 서명도 발급처도 진짜라 다 통과한다.
     * 이를 막는 것이 `aud`다.
     *
     * `aud`는 값이 여러 개일 수 있어 목록으로 다룬다.
     */
    private void validateAudience(Claims claims) {
        Set<String> tokenAudiences = claims.getAudience();
        if (tokenAudiences == null || tokenAudiences.stream().noneMatch(audiences::contains)) {
            log.debug("구글 토큰 audience 불일치: {}", tokenAudiences);
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    /**
     * 구글 클레임을 서비스 공통 모양으로 옮긴다.
     *
     * `sub`가 구글의 사용자 식별자다. 이메일은 사용자가 바꿀 수 있어 사람을 식별하는 데
     * 쓰지 않는다. (tour_api_erd.md 결정 4)
     */
    private OAuthUserInfo toUserInfo(Claims claims) {
        return new OAuthUserInfo(
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("name", String.class),
                claims.get("picture", String.class));
    }

    @Override
    public ProviderType getType() {
        return ProviderType.GOOGLE;
    }
}
