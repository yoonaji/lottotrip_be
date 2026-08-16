package com.lottotrip.mission.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude API 접속 설정. (roadmap 8-5, 결정 17)
 *
 * `@ConfigurationProperties`는 `application.yml`의 `anthropic.*` 값을
 * 이 record의 필드에 이름을 맞춰 담아 주는 표시다. TourAPI 설정과 같은 방식이며,
 * 이유도 같다 — **키를 소스에 박지 않기 위해서다.**
 *
 * @param apiKey         Claude API 키. **비어 있으면 미션 생성만 템플릿 방식으로 되돌아가고**
 *                       서버 기동과 나머지 API에는 영향이 없다(TourAPI 키와 같은 규칙)
 * @param model          쓸 모델. 기본은 `claude-haiku-4-5` — 미션 문구 3개를 만드는
 *                       단순한 작업이고, 이 호출이 draw 응답 시간에 그대로 얹히므로 가장 빠른 것을 고른다
 * @param maxTokens      한 번에 받을 응답 길이 상한. 문구 몇 줄이라 크게 잡을 이유가 없다
 * @param timeoutSeconds 응답을 기다릴 시간. **넘기면 예외가 나고 템플릿으로 내려간다.**
 *                       SDK 기본값은 10분이라 그대로 두면 draw가 10분간 멈춰 있을 수 있다
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        String model,
        int maxTokens,
        int timeoutSeconds
) {

    private static final String DEFAULT_MODEL = "claude-haiku-4-5";
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final int DEFAULT_TIMEOUT_SECONDS = 8;

    /** 값이 필드에 담기기 직전에 끼어들어 기본값을 채운다(TourApiProperties와 같은 방식). */
    public AnthropicProperties {
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        if (maxTokens <= 0) {
            maxTokens = DEFAULT_MAX_TOKENS;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
    }

    /** 키가 있어야 부를 수 있다. 없으면 아예 시도하지 않고 템플릿으로 간다. */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
