package com.lottotrip.auth.oauth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

/**
 * 카카오 액세스 토큰 검증. (roadmap 4-4-1)
 *
 * 카카오는 구글·애플과 검증 방식이 다르다. 구글·애플은 받은 토큰이 JWT라서 서명만 계산하면
 * 진짜인지 알 수 있지만, 카카오가 주는 access token은 그냥 불투명한 문자열이라
 * 카카오에 직접 물어봐야 한다. 그래서 여기만 외부 HTTP 호출이 들어간다.
 *
 * 카카오를 두 번 부른다.
 *   1. `/v1/user/access_token_info` — 이 토큰이 우리 앱을 위해 발급됐는가 (`app_id` 대조)
 *   2. `/v2/user/me` — 사용자 정보. 조회가 성공하면 토큰이 유효한 것이고, 401이면 유효하지 않은 것이다
 *
 * ⚠️ 1번을 빠뜨리면 인증이 뚫린다. 공격자가 카카오에 자기 앱을 등록해 로그인하면 카카오가 발급한
 * 진짜 토큰을 얻는데, 그 토큰으로도 2번이 200을 돌려주기 때문이다. 서명도 만료도 멀쩡하니
 * 2번만으로는 걸러낼 방법이 없다. 구글이 `aud`로 막는 것과 같은 공격이고,
 * 카카오에서 같은 역할을 하는 것이 `app_id`다.
 *
 * 다만 카카오 회원번호는 앱마다 다르게 발급되므로, 이 구멍으로 기존 회원의 계정을 빼앗을 수는
 * 없었다. 남의 앱 토큰으로 만들어지는 것은 우리 서비스의 새 계정이다.
 */
@Slf4j
@Component
public class KakaoTokenVerifier extends SocialTokenVerifier {

    private final RestClient restClient;
    private final String userInfoUri;
    private final String tokenInfoUri;
    private final Long appId;

    public KakaoTokenVerifier(RestClient.Builder restClientBuilder, KakaoOAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.userInfoUri = properties.userInfoUri();
        this.tokenInfoUri = properties.tokenInfoUri();
        this.appId = properties.appId();

        if (appId == null) {
            log.warn("카카오 앱 ID(oauth.kakao.app-id)가 설정되지 않아 카카오 로그인이 비활성 상태입니다.");
        }
    }

    @Override
    protected OAuthUserInfo doVerify(String token) {
        requireConfigured();
        validateIssuedToOurApp(token);
        return fetchUserInfo(token);
    }

    /**
     * 앱 ID가 없으면 로그인을 막는다.
     *
     * 설정이 없다고 대조를 건너뛰면 다른 앱의 토큰으로도 로그인할 수 있는 상태로 되돌아간다.
     * 그래서 "검사를 못 하면 아예 받지 않는" 쪽을 택한다.
     * 구글이 클라이언트 ID 미설정 시 `BAD_REQUEST`를 주는 것과 같은 취급이다.
     * 실제로 둘 다 "이 provider로는 지금 로그인할 수 없다"는 상태다.
     */
    private void requireConfigured() {
        if (appId == null) {
            log.warn("카카오 앱 ID 미설정 상태에서 로그인 시도가 들어왔습니다.");
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    /**
     * 이 토큰이 우리 앱을 위해 발급된 것인지 확인한다.
     *
     * 대조에 실패하면 사용자 정보는 조회하지 않는다. 어차피 거절할 토큰으로 카카오를
     * 한 번 더 부를 이유가 없다.
     *
     * `app_id`가 아예 없는 응답도 거절한다. 대조할 값이 없는 것을 "같다"고 볼 수는 없다.
     */
    private void validateIssuedToOurApp(String token) {
        KakaoTokenInfoResponse info = call(tokenInfoUri, token, KakaoTokenInfoResponse.class);

        if (info == null || !Objects.equals(info.appId(), appId)) {
            log.debug("카카오 토큰 app_id 불일치: {}", info == null ? null : info.appId());
            throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
        }
    }

    private OAuthUserInfo fetchUserInfo(String token) {
        KakaoUserResponse response = call(userInfoUri, token, KakaoUserResponse.class);
        return response == null ? null : response.toOAuthUserInfo();
    }

    /**
     * 액세스 토큰을 실어 카카오 API를 부른다. 두 호출의 에러 처리가 같아서 한 곳으로 모았다.
     *
     * 응답 상태에 따라 에러를 나눈다. 무엇이 잘못됐는지에 따라 사용자가 할 일이 다르기 때문이다.
     *   - 4xx — 토큰이 잘못됐다. 다시 로그인해야 한다 → `INVALID_PROVIDER_TOKEN`(401)
     *   - 5xx·연결 실패 — 카카오 측 이슈. 잠시 뒤 다시 시도하면 된다 → `SERVICE_UNAVAILABLE`(503)
     */
    private <T> T call(String uri, String token, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    // retrieve()부터가 "응답을 다루는 단계"다. 여기서부터 상태 코드와 본문을 처리한다.
                    .retrieve()
                    // onStatus는 "이 상태 코드가 오면 이렇게 처리하라"는 규칙이다.
                    // 등록하지 않으면 RestClient가 자기 예외를 던져 버려 우리 에러 코드로 바꿀 수 없다.
                    .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                        log.debug("카카오 토큰 거절: uri={}, status={}", uri, res.getStatusCode());
                        throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                        log.warn("카카오 API 오류: uri={}, status={}", uri, res.getStatusCode());
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(responseType);

        } catch (CustomException e) {
            // 위 onStatus에서 우리가 던진 예외다. 그대로 올려보낸다.
            throw e;
        } catch (RestClientException e) {
            // 연결 실패·타임아웃·응답 파싱 실패가 여기로 온다. 셋 다 토큰 문제가 아니다.
            // 예: 점검 페이지(HTML)가 200으로 오면 JSON 변환에 실패한다.
            log.warn("카카오 API 호출 실패: uri={}, {}", uri, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public ProviderType getType() {
        return ProviderType.KAKAO;
    }
}
