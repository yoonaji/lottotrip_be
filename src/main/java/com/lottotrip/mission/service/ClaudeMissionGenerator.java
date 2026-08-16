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
 * <p>6-5에서 "구현체만 갈아 끼운다"를 전제로 인터페이스를 먼저 만들어 뒀고, 이제 그 자리를 채운다.
 * {@link MissionGenerator} 인터페이스와 {@code MissionMatcher}는 손대지 않았다 —
 * 4-3의 {@code SocialTokenVerifier}와 같은 구조다.
 *
 * <p><b>{@code @Primary}가 붙은 이유</b> — 이제 {@code MissionGenerator}를 구현한 빈이 둘이다
 * (이것과 {@link TemplateMissionGenerator}). 스프링은 둘 중 어느 것을 넣어야 할지 몰라
 * 주입 시점에 실패한다. {@code @Primary}는 "고민되면 이걸 써라"는 표시다.
 *
 * <p><b>템플릿 구현을 지우지 않은 이유</b> — 여기서 <b>폴백</b>으로 쓴다. 키가 없거나,
 * API가 장애이거나, 타임아웃이 나도 미션이 통째로 사라지면 안 된다. 미션은 슬롯에 곁들이는
 * 정보라, 그것 때문에 여행지 뽑기 전체가 실패하는 것은 손해가 더 크다.
 *
 * <p>⚠️ <b>생성 방식은 또 바뀔 수 있다는 전제로 만들었다</b>(사용자 지시). 바뀌는 지점은
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
     * <p>⚠️ 잠정값이다(미확정 항목 "6-5 ③"). {@code reward_point}가 NOT NULL이라 정해 둔 것이고,
     * 기준이 생기면 두 구현체의 상수를 함께 고친다.
     */
    private static final int DEFAULT_REWARD_POINT = 100;

    /** {@code missions.title}이 VARCHAR(100)이다. 넘으면 저장 시점에 터진다. */
    private static final int MAX_TITLE_LENGTH = 100;

    private final ClaudeMissionClient client;
    private final TemplateMissionGenerator fallback;

    /**
     * {@inheritDoc}
     *
     * <p>흐름: 키 확인 → Claude 호출 → 쓸 수 있는 문구만 골라 {@link Mission}으로 변환.
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
     * Claude에게 문구를 받아 미션으로 옮긴다. 실패하면 <b>빈 목록</b>을 돌려준다.
     *
     * <p><b>예외를 여기서 막는 이유</b> — 이 호출은 draw 도중에 일어난다. 예외가 그대로 올라가면
     * 장소는 멀쩡히 뽑혔는데 미션 생성 실패 때문에 슬롯 응답 전체가 500이 된다.
     * {@code Exception}을 통째로 잡는 것은 보통 피해야 하지만, <b>여기서는 "바깥이 어떻게 실패하든
     * 우리는 템플릿으로 간다"가 정확히 우리가 원하는 규칙</b>이다.
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

    /**
     * 저장해도 되는 문구인지 본다.
     *
     * <p>모델이 준 값을 그대로 믿지 않는 지점이다. 제목이 비면 사용자에게 빈 미션이 보이고,
     * 100자를 넘으면 INSERT가 실패한다. <b>길면 잘라 붙이지 않고 버린다</b> —
     * 문장이 중간에서 끊긴 미션이 더 이상하고, 모자란 만큼은 다음 draw에서 다시 채워진다.
     */
    private boolean usable(MissionCopy copy) {
        if (copy == null || copy.title() == null || copy.title().isBlank()) {
            return false;
        }
        return copy.title().trim().length() <= MAX_TITLE_LENGTH;
    }
}
