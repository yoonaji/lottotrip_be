package com.lottotrip.mission.service;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.place.entity.Place;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * 뽑힌 장소에 제시할 미션을 고른다. (roadmap 6-5, 2026-08-13 회의 확정)
 *
 * 규칙: 장소에 등록된 미션이 {@link #REQUIRED_MISSION_COUNT}개 미만이면 생성해 채운 뒤,
 * 확보된 전체에서 랜덤 1개를 제시한다. **미션이 하나도 없는 장소가 뽑혀도 응답이 비지 않게** 하려는 것이다.
 *
 * 생성 방식은 {@link MissionGenerator}에 맡긴다 — 아직 보류라 구현체가 바뀔 수 있고,
 * 이 클래스는 "몇 개가 필요한지 세고, 채우고, 고르는" 일만 한다.
 */
@Slf4j
@Service
public class MissionMatcher {

    /**장소마다 확보해 둘 미션 수*/
    private static final int REQUIRED_MISSION_COUNT = 3;

    private final MissionRepository missionRepository;
    private final MissionGenerator missionGenerator;

    /** 난수원. 공급자로 받는 이유는 `RealtimePlaceFinder`와 같다(스레드 안전 + 테스트 고정). */
    private final Supplier<RandomGenerator> randomSource;

    @Autowired
    public MissionMatcher(MissionRepository missionRepository, MissionGenerator missionGenerator) {
        this(missionRepository, missionGenerator, ThreadLocalRandom::current);
    }

    /** 테스트에서 난수를 고정하기 위한 생성자. */
    MissionMatcher(MissionRepository missionRepository, MissionGenerator missionGenerator,
                   Supplier<RandomGenerator> randomSource) {
        this.missionRepository = missionRepository;
        this.missionGenerator = missionGenerator;
        this.randomSource = randomSource;
    }

    /**
     * 이 장소에 제시할 미션 하나를 고른다.
     *
     * 비어 있을 수 있다. = 생성기가 하나도 만들지 못하고 기존 미션도 없는 경우
     * 장소는 정상적으로 뽑혔으므로 미션 없이라도 응답이 나가는 편이 낫다고 판단했다.
     *
     * ⚠️ 동시 요청은 아직 막지 않았다. 같은 장소로 두 요청이
     * 동시에 오면 각자 생성해 비슷한 미션이 중복 저장될 수 있다.
     */
    @Transactional
    public Optional<Mission> matchFor(Place place) {
        List<Mission> missions = new ArrayList<>(missionRepository.findByPlaceId(place.getId()));

        int shortfall = REQUIRED_MISSION_COUNT - missions.size();
        if (shortfall > 0) {
            missions.addAll(generateAndSave(place, shortfall));
        }

        if (missions.isEmpty()) {
            log.warn("미션을 하나도 확보하지 못했습니다 — 장소 {}({}). 미션 없이 응답합니다",
                    place.getName(), place.getId());
            return Optional.empty();
        }
        return Optional.of(missions.get(randomSource.get().nextInt(missions.size())));
    }

    /**미션 LLM 통해서 생성 후 저장.*/
    private List<Mission> generateAndSave(Place place, int shortfall) {
        List<Mission> generated = missionGenerator.generate(place, shortfall);
        if (generated == null || generated.isEmpty()) {
            return List.of();
        }
        if (generated.size() < shortfall) {
            log.debug("미션 생성이 요청보다 적습니다 — 장소 {} / 요청 {} / 생성 {}",
                    place.getId(), shortfall, generated.size());
        }
        return missionRepository.saveAll(generated);
    }
}
