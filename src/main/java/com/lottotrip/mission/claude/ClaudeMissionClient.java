package com.lottotrip.mission.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.lottotrip.place.entity.Place;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Claude API에 미션 문구를 요청하는 부분. (roadmap 8-5, 결정 17)
 *
 * **생성기({@link com.lottotrip.mission.service.ClaudeMissionGenerator})와 나눠 둔 이유** —
 * 이 클래스는 "바깥에 나갔다 오는" 일만 한다. 나눠 두면 생성기 쪽 테스트에서 이것만 가짜로
 * 바꿔 끼울 수 있어, **네트워크 없이 폴백과 변환 규칙을 검증**할 수 있다.
 * 4-4의 `KakaoTokenVerifier`가 API 호출만 맡는 것과 같은 구조다.
 *
 * **실패하면 예외를 그대로 올린다.** "실패를 어떻게 수습할지"(템플릿으로 내려갈지)는
 * 부르는 쪽의 판단이라 여기서 삼키지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeMissionClient {

    private final AnthropicProperties properties;

    /**
     * SDK 클라이언트. **처음 쓸 때 만든다(지연 생성).**
     *
     * 기동 시점에 만들면 키가 비어 있는 개발 환경에서 서버가 아예 못 뜬다.
     * 키가 없으면 {@link #write}가 호출되지 않으므로 이 필드도 끝까지 비어 있는다.
     *
     * `volatile`은 "여러 스레드가 이 필드를 볼 때 서로 최신 값을 보게 하라"는 표시다.
     * 웹 요청은 스레드가 여러 개라 이 표시가 없으면 한 스레드가 만든 값을 다른 스레드가 못 볼 수 있다.
     */
    private volatile AnthropicClient client;

    /** 키가 설정돼 있는가. 없으면 부르는 쪽이 템플릿으로 내려간다. */
    public boolean enabled() {
        return properties.enabled();
    }

    /**
     * 이 장소에 쓸 미션 문구를 `count`개 요청한다.
     *
     * **구조화 출력(structured output)을 쓴다.** {@link MissionCopies} record로부터 SDK가
     * JSON 스키마를 만들어 함께 보내면, 모델은 그 형식의 JSON만 답한다. 자유 문장을 받아
     * 우리가 잘라 쓰면 모델이 형식을 조금만 바꿔도 파싱이 깨진다.
     *
     * @return 받은 문구들. 개수·내용은 **검증하지 않은 상태**이며, 거르는 것은 부르는 쪽의 몫이다
     * @throws RuntimeException 통신 실패·타임아웃·인증 실패 등 SDK가 던지는 모든 예외
     */
    public List<MissionCopy> write(Place place, int count) {
        StructuredMessageCreateParams<MissionCopies> params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .outputConfig(MissionCopies.class)
                .addUserMessage(prompt(place, count))
                .build();

        return client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text().missions())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    /**
     * 모델에게 줄 지시문.
     *
     * **"도착하면 달성되는 것만"이라는 제약이 핵심이다.** 완료 판정은 GPS 위치 하나뿐인데
     * (8-1) 문구가 "일몰 사진 찍기"처럼 구체적이면, 해가 중천일 때 서 있기만 해도 완료된다.
     * 사용자는 속았다고 느낀다. 판정할 수 있는 것만 시키면 그 간극이 생기지 않는다.
     */
    private String prompt(Place place, int count) {
        return """
                여행지에 방문한 사람에게 제시할 미션 문구를 %d개 만들어 주세요.

                장소 이름: %s
                장소 분류: %s

                조건:
                - 그 장소에 도착하기만 하면 달성되는 미션만 만듭니다. 완료 여부를 GPS 위치로만 판정하기 때문입니다.
                - 특정 시간대(일몰·야경), 날씨, 영업시간, 유료 입장, 준비물이 필요한 미션은 만들지 마세요.
                - 장소마다 다른 문구여야 합니다. 이름만 바꾼 같은 문장은 안 됩니다.
                - 제목은 40자 이내의 한 문장으로, 장소 이름을 포함합니다.
                - 한국어로 작성합니다.
                """.formatted(count, place.getName(), place.getCategory());
    }

    /**
     * 처음 부를 때 SDK 클라이언트를 만들어 재사용한다.
     *
     * **타임아웃을 반드시 건다.** SDK 기본값은 10분이라, 그대로 두면 Claude가 늦을 때
     * 슬롯 돌리기 응답이 10분간 멈춰 있는다. 짧게 잡고 넘기면 템플릿으로 내려가는 편이 낫다.
     *
     * `synchronized`는 "이 블록은 한 번에 한 스레드만 지나간다"는 뜻이다.
     * 없으면 동시에 들어온 두 요청이 클라이언트를 각각 만들 수 있다(치명적이진 않지만 낭비다).
     */
    private AnthropicClient client() {
        AnthropicClient created = client;
        if (created == null) {
            synchronized (this) {
                created = client;
                if (created == null) {
                    log.debug("Claude 클라이언트 생성: model={}, timeout={}s",
                            properties.model(), properties.timeoutSeconds());
                    created = AnthropicOkHttpClient.builder()
                            .apiKey(properties.apiKey())
                            .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                            .maxRetries(1)
                            .build();
                    client = created;
                }
            }
        }
        return created;
    }

    /**
     * 응답을 담을 그릇. 목록을 그냥 배열로 받지 않고 한 겹 감싸는 이유는
     * 구조화 출력의 최상위가 **객체**여야 하기 때문이다(JSON 스키마 규칙).
     */
    public record MissionCopies(

            @JsonPropertyDescription("만들어진 미션 문구 목록")
            List<MissionCopy> missions) {
    }
}
