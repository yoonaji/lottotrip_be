package com.lottotrip.auth.oauth;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 소셜 서비스의 공개키(JWKS)를 받아 와 캐시한다.
 *
 * 구글·애플이 같은 방식(JWKS)을 쓰므로 이 클래스는 **주소만 바꿔서 재사용**한다.
 * 4-4-3에서 애플을 붙일 때 이 클래스를 그대로 쓴다.
 *
 * 스프링 빈이 아니다. provider마다 주소가 다르므로 각 검증 구현체가 자기 것을 하나씩 만들어 갖는다.
 */
@Slf4j
public class JwksProvider {

    /**
     * 공개키를 캐시하는 시간.
     *
     * 로그인마다 구글에 공개키를 받으러 가면 두 가지가 나빠진다. 응답이 그만큼 느려지고,
     * 구글이 잠깐 느려질 때 우리 로그인도 같이 느려진다. 공개키는 자주 바뀌지 않으므로 캐시한다.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestClient restClient;
    private final String jwksUri;

    /**
     * `volatile`은 "여러 스레드가 이 값을 볼 때 항상 최신을 보게 하라"는 표시다.
     *
     * 웹 서버는 요청마다 다른 스레드가 이 코드를 지나간다. 이 표시가 없으면 A 스레드가 갱신한
     * 캐시를 B 스레드가 한동안 못 볼 수 있다.
     */
    private volatile Map<String, PublicKey> cachedKeys = Map.of();
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public JwksProvider(RestClient restClient, String jwksUri) {
        this.restClient = restClient;
        this.jwksUri = jwksUri;
    }

    /**
     * 토큰 헤더의 `kid`에 해당하는 공개키를 찾는다.
     *
     * 캐시에 없으면 **한 번 더 받아온다.** 구글은 공개키를 주기적으로 교체하는데,
     * 캐시에 없다고 바로 실패시키면 교체 직후 모든 로그인이 실패하기 때문이다.
     */
    public PublicKey findKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            log.debug("토큰 헤더에 kid가 없음: {}", jwksUri);
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }

        PublicKey key = currentKeys().get(keyId);
        if (key == null) {
            log.debug("캐시에 없는 kid={}. 키 교체로 보고 다시 받아온다", keyId);
            key = fetchKeys().get(keyId);
        }
        if (key == null) {
            log.debug("JWKS에 없는 kid={}", keyId);
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
        return key;
    }

    private Map<String, PublicKey> currentKeys() {
        if (Instant.now().isAfter(cacheExpiresAt)) {
            return fetchKeys();
        }
        return cachedKeys;
    }

    /**
     * JWKS를 실제로 받아 와 캐시에 넣는다.
     *
     * `synchronized`는 "한 번에 한 스레드만 이 메서드에 들어온다"는 뜻이다.
     * 없으면 동시에 들어온 로그인 요청 수십 개가 각자 구글을 호출한다.
     */
    private synchronized Map<String, PublicKey> fetchKeys() {
        JwkSetResponse response;
        try {
            response = restClient.get()
                    .uri(jwksUri)
                    .retrieve()
                    .body(JwkSetResponse.class);
        } catch (RestClientException e) {
            // 공개키를 못 받은 것은 토큰이 잘못된 것과 무관하다. 사용자는 기다렸다 다시 하면 된다.
            log.warn("JWKS 조회 실패 uri={}: {}", jwksUri, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        if (response == null || response.keys() == null) {
            log.warn("JWKS 응답이 비어 있음 uri={}", jwksUri);
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        Map<String, PublicKey> keys = new HashMap<>();
        for (JwkSetResponse.Jwk jwk : response.keys()) {
            if (!jwk.isRsa() || jwk.kid() == null) {
                continue;
            }
            try {
                keys.put(jwk.kid(), jwk.toPublicKey());
            } catch (Exception e) {
                // 키 하나가 이상하다고 전부 버리면, 멀쩡한 나머지 키로 될 로그인까지 막힌다.
                log.warn("공개키 변환 실패 kid={}: {}", jwk.kid(), e.getMessage());
            }
        }

        cachedKeys = Map.copyOf(keys);
        cacheExpiresAt = Instant.now().plus(CACHE_TTL);
        return cachedKeys;
    }
}
