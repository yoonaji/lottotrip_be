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

/**
 * 카카오 액세스 토큰 검증. (roadmap 4-4-1)
 *
 * 카카오는 구글·애플과 검증 방식이 다르다. 구글·애플은 받은 토큰이 JWT라서 서명만 계산하면
 * 진짜인지 알 수 있지만, 카카오가 주는 access token은 그냥 불투명한 문자열이라
 * 카카오에 직접 물어봐야 한다. 그래서 여기만 외부 HTTP 호출이 들어간다.
 *
 * "토큰이 유효한가"를 따로 묻는 API가 있는 게 아니라, 사용자 정보 조회가 성공하면 유효한 것이고
 * 401이 오면 유효하지 않은 것이다.
 */
@Slf4j
@Component
public class KakaoTokenVerifier extends SocialTokenVerifier {

    private final RestClient restClient;
    private final String userInfoUri;

    public KakaoTokenVerifier(RestClient.Builder restClientBuilder, KakaoOAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.userInfoUri = properties.userInfoUri();
    }

    /**
     * 액세스 토큰으로 카카오 사용자 정보를 조회한다.
     *
     * 응답 상태에 따라 에러를 나눈다. 무엇이 잘못됐는지에 따라 사용자가 할 일이 다르기 때문이다.
     *   - 4xx — 토큰이 잘못됐다. 다시 로그인해야 한다 → `INVALID_PROVIDER_TOKEN`(401)
     *   - 5xx·연결 실패 — 카카오 측 이슈. 잠시 뒤 다시 시도하면 된다 → `SERVICE_UNAVAILABLE`(503)
     */
    @Override
    protected OAuthUserInfo doVerify(String token) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    // retrieve()부터가 "응답을 다루는 단계"다. 여기서부터 상태 코드와 본문을 처리한다.
                    .retrieve()
                    // onStatus는 "이 상태 코드가 오면 이렇게 처리하라"는 규칙이다.
                    // 등록하지 않으면 RestClient가 자기 예외를 던져 버려 우리 에러 코드로 바꿀 수 없다.
                    .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                        log.debug("카카오 토큰 거절: status={}", res.getStatusCode());
                        throw new CustomException(ErrorCode.INVALID_PROVIDER_TOKEN);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                        log.warn("카카오 사용자 정보 API 오류: status={}", res.getStatusCode());
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(KakaoUserResponse.class);

            return response == null ? null : response.toOAuthUserInfo();

        } catch (CustomException e) {
            // 위 onStatus에서 우리가 던진 예외다. 그대로 올려보낸다.
            throw e;
        } catch (RestClientException e) {
            // 연결 실패·타임아웃·응답 파싱 실패가 여기로 온다. 셋 다 토큰 문제가 아니다.
            // 예: 점검 페이지(HTML)가 200으로 오면 JSON 변환에 실패한다.
            log.warn("카카오 사용자 정보 조회 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public ProviderType getType() {
        return ProviderType.KAKAO;
    }
}
