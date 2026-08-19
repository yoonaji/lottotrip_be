package com.lottotrip.mission.claude;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Claude가 만들어 준 미션 문구 한 개. (roadmap 8-5)
 *
 * **Entity가 아니라 별도의 record인 이유** — 모델이 준 값은 아직 우리 것이 아니다.
 * 제목이 비어 있거나 컬럼 길이를 넘을 수 있어서, 그대로 `Mission`으로 받으면
 * 검사 없이 DB로 흘러간다. 여기서 한 번 받아 두고 `ClaudeMissionGenerator`가 걸러 옮긴다.
 *
 * `@JsonPropertyDescription`은 **모델에게 이 필드가 무엇인지 알려 주는 설명**이다.
 * SDK가 이 record로부터 JSON 스키마를 만들어 요청에 함께 보내고, 모델은 그 스키마에 맞는
 * JSON만 답한다(구조화 출력). 자유 문장을 받아 정규식으로 자르면 형식이 조금만 달라져도 깨진다.
 */
public record MissionCopy(

        @JsonPropertyDescription("미션 제목. 장소 이름을 포함한 한 문장, 40자 이내")
        String title,

        @JsonPropertyDescription("미션 수행 안내. 한두 문장")
        String description) {
}
