package com.lottotrip.mission.service;

import com.lottotrip.mission.claude.ClaudeMissionClient;
import com.lottotrip.mission.claude.MissionCopy;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Claude API로 미션 문구를 만드는 구현체. (roadmap 8-5, 결정 17)
 *
 * 아직 미션 구현 방식이 확정 아님.
 * {@link MissionGenerator} 인터페이스와 `MissionMatcher`는 손대지 않았다
 *
 * 템플릿 구현을 지우지 않은 이유 — 폴백으로 쓴다. 키가 없거나,
 * API가 장애이거나, 타임아웃이 나도 미션이 통째로 사라지면 안 된다. 미션은 슬롯에 곁들이는
 * 정보라, 그것 때문에 여행지 뽑기 전체가 실패하는 것은 손해가 더 크다.
 *
 * ⚠️ 생성 방식은 또 바뀔 수 있다는 전제로 만들었다(사용자 지시). 바뀌는 지점은
 * 이 클래스 하나이고, 슬롯·매칭·스키마는 그대로다.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class ClaudeMissionGenerator implements MissionGenerator {

    /**
     * 보상 점수. 템플릿 구현과 같은 값을 쓴다.
     *
     * 잠정값. `reward_point`가 NOT NULL이라 정해 둔 것이고,
     * 기준이 생기면 두 구현체의 상수를 함께 고친다.
     */
    private static final int DEFAULT_REWARD_POINT = 100;

    /** `missions.title`이 VARCHAR(100)이다. 넘으면 저장 시점에 터진다. */
    private static final int MAX_TITLE_LENGTH = 100;

    private final ClaudeMissionClient client;
    private final TemplateMissionGenerator fallback;

    /**
     * {@inheritDoc}
     *
     * 흐름: 키 확인 → Claude 호출 → 쓸 수 있는 문구만 골라 {@link Mission}으로 변환.
     * 어느 단계에서든 결과가 비면 템플릿으로 내려간다.
     */
    @Override
    public List<Mission> generate(Place place, int count) {
        if (count <= 0) {
            return List.of();
        }
        if (!client.enabled()) {
            log.debug("ANTHROPIC_API_KEY가 없어 템플릿으로 미션을 만듭니다 — 장소 {}", place.getId());
            return fallback.generate(place, count);
        }

        List<Mission> missions = requestCopies(place, count);
        if (missions.isEmpty()) {
            log.warn("Claude가 쓸 만한 문구를 주지 않아 템플릿으로 대체합니다 — 장소 {}({})",
                    place.getName(), place.getId());
            return fallback.generate(place, count);
        }
        return missions;
    }

    /**
     * Claude에게 문구를 받아 미션으로 옮긴다. 실패하면 빈 목록을 돌려준다.
     *
     * 예외를 여기서 막는 이유 — 이 호출은 draw 도중에 일어난다. 예외가 그대로 올라가면
     * 장소는 멀쩡히 뽑혔는데 미션 생성 실패 때문에 슬롯 응답 전체가 500이 된다.
     * `Exception`을 통째로 잡는 것은 보통 피해야 하지만, 여기서는 "바깥이 어떻게 실패하든
     * 우리는 템플릿으로 간다"가 정확히 우리가 원하는 규칙이다.
     */
    private List<Mission> requestCopies(Place place, int count) {
        try {
            List<MissionCopy> copies = client.write(place, count);
            if (copies == null) {
                return List.of();
            }
            return copies.stream()
                    .filter(this::usable)
                    .limit(count)
                    .map(copy -> Mission.create(place, copy.title().trim(), copy.description(),
                            null, DEFAULT_REWARD_POINT))
                    .toList();
        } catch (Exception e) {
            log.warn("Claude 미션 생성 실패 — 장소 {}({}): {}", place.getName(), place.getId(), e.toString());
            return List.of();
        }
    }

    /**저장해도 되는 문구인지 확인*/
    private boolean usable(MissionCopy copy) {
        if (copy == null || copy.title() == null || copy.title().isBlank()) {
            return false;
        }
        return copy.title().trim().length() <= MAX_TITLE_LENGTH;
    }
}
