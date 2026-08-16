package com.lottotrip.mission.service;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 매칭 검증. (roadmap 6-5, 2026-08-13 회의 확정)
 *
 * **확정된 규칙:** 장소에 등록된 미션이 **3개** 미만이면 생성해 채운 뒤,
 * 확보된 전체에서 랜덤 1개를 제시한다. 미션이 하나도 없는 장소를 뽑아도 응답이 비지 않게 하려는 것이다.
 *
 * **생성 방식(LLM vs 키워드)은 보류다.** 그래서 {@link MissionGenerator}를 인터페이스로 두고
 * 여기서는 **매칭·선택 로직만** 검증한다. 테스트는 가짜 생성기를 끼워 "몇 개를 요청했는지"와
 * "받은 것을 어떻게 다루는지"에 집중한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MissionMatcherTest extends PostgresContainerSupport {

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private PlaceRepository placeRepository;

    private Place place;
    private int sequence;

    /**
     * 시드를 고정한 난수기. **인스턴스를 하나 두고 계속 쓴다.**
     *
     * `() -> new Random(42)`처럼 공급자 안에서 만들면 부를 때마다 같은 시드의
     * 새 난수기가 생겨 **항상 같은 첫 값**이 나온다. 난수가 고정되는 게 아니라 한 값에 박힌다.
     */
    private Random random;

    @BeforeEach
    void setUp() {
        sequence = 0;
        random = new Random(42);
        place = placeRepository.save(Place.builder()
                .contentId("place-1")
                .contentTypeId("12")
                .name("사천진해변")
                .category(TravelCategory.NATURE_ATTRACTION)
                .latitude(37.8021)
                .longitude(128.8954)
                .build());
    }

    /** 요청한 개수만큼 미션을 만들어 주는 가짜 생성기. 몇 개를 요청받았는지 기록한다. */
    private static class RecordingGenerator implements MissionGenerator {
        private final List<Integer> requestedCounts = new ArrayList<>();
        private final int produceAtMost;

        RecordingGenerator() {
            this(Integer.MAX_VALUE);
        }

        /** 요청보다 적게 주는 상황을 만들기 위한 생성자. */
        RecordingGenerator(int produceAtMost) {
            this.produceAtMost = produceAtMost;
        }

        @Override
        public List<Mission> generate(Place place, int count) {
            requestedCounts.add(count);
            List<Mission> generated = new ArrayList<>();
            for (int i = 0; i < Math.min(count, produceAtMost); i++) {
                generated.add(Mission.create(place, "생성 미션 " + i, "설명", null, 100));
            }
            return generated;
        }
    }

    private MissionMatcher matcherWith(MissionGenerator generator) {
        return new MissionMatcher(missionRepository, generator, () -> random);
    }

    private Mission savedMission(String title) {
        return missionRepository.save(
                Mission.create(place, title + (++sequence), "설명", null, 100));
    }

    @Test
    @DisplayName("미션이 3개면 새로 만들지 않고 그중 하나를 준다")
    void picksFromExistingWhenEnough() {
        savedMission("기존");
        savedMission("기존");
        savedMission("기존");
        RecordingGenerator generator = new RecordingGenerator();

        Optional<Mission> picked = matcherWith(generator).matchFor(place);

        assertThat(picked).isPresent();
        assertThat(picked.get().getTitle()).startsWith("기존");
        assertThat(generator.requestedCounts).isEmpty();      // 생성기를 부르지 않았다
        assertThat(missionRepository.findByPlaceId(place.getId())).hasSize(3);
    }

    @Test
    @DisplayName("미션이 하나도 없으면 3개를 만들어 채운다")
    void generatesThreeWhenNoneExists() {
        // 회의에서 정한 핵심 동작이다. 미션이 없는 장소가 뽑혀도 응답이 비면 안 된다.
        RecordingGenerator generator = new RecordingGenerator();

        Optional<Mission> picked = matcherWith(generator).matchFor(place);

        assertThat(picked).isPresent();
        assertThat(generator.requestedCounts).containsExactly(3);
        assertThat(missionRepository.findByPlaceId(place.getId())).hasSize(3);
    }

    @Test
    @DisplayName("모자란 만큼만 만든다")
    void generatesOnlyTheShortfall() {
        savedMission("기존");
        RecordingGenerator generator = new RecordingGenerator();

        matcherWith(generator).matchFor(place);

        assertThat(generator.requestedCounts).containsExactly(2);   // 3 - 1
        assertThat(missionRepository.findByPlaceId(place.getId())).hasSize(3);
    }

    @Test
    @DisplayName("이미 3개보다 많아도 줄이거나 만들지 않는다")
    void leavesSurplusAlone() {
        for (int i = 0; i < 5; i++) {
            savedMission("기존");
        }
        RecordingGenerator generator = new RecordingGenerator();

        Optional<Mission> picked = matcherWith(generator).matchFor(place);

        assertThat(picked).isPresent();
        assertThat(generator.requestedCounts).isEmpty();
        assertThat(missionRepository.findByPlaceId(place.getId())).hasSize(5);
    }

    @Test
    @DisplayName("만들어진 미션이 실제로 저장된다 — 다음 요청은 생성 없이 끝난다")
    void persistsGeneratedMissions() {
        // 저장하지 않으면 같은 장소가 뽑힐 때마다 매번 새로 만든다.
        // 생성이 LLM으로 정해지면 그 비용이 요청마다 반복된다.
        RecordingGenerator generator = new RecordingGenerator();
        MissionMatcher matcher = matcherWith(generator);

        matcher.matchFor(place);
        matcher.matchFor(place);

        assertThat(generator.requestedCounts).containsExactly(3);   // 두 번째는 부르지 않았다
        assertThat(missionRepository.findByPlaceId(place.getId())).hasSize(3);
    }

    @Test
    @DisplayName("생성기가 요청보다 적게 줘도 있는 것 중에서 고른다")
    void toleratesGeneratorShortfall() {
        // 생성이 실패하거나 일부만 성공할 수 있다. 3개를 못 채웠다고 슬롯을 실패시키면
        // 미션은 곁들이는 것인데 본 기능이 통째로 막힌다.
        RecordingGenerator generator = new RecordingGenerator(1);

        Optional<Mission> picked = matcherWith(generator).matchFor(place);

        assertThat(picked).isPresent();
        assertThat(missionRepository.findByPlaceId(place.getId())).hasSize(1);
    }

    @Test
    @DisplayName("생성기가 하나도 못 주고 기존도 없으면 빈 값이다 — 예외를 던지지 않는다")
    void returnsEmptyWhenNothingAvailable() {
        // 여기서 예외를 던지면 미션 때문에 슬롯 전체가 실패한다.
        // 장소는 정상적으로 뽑혔으므로 미션 없이라도 응답이 나가는 편이 낫다.
        Optional<Mission> picked = matcherWith((p, count) -> List.of()).matchFor(place);

        assertThat(picked).isEmpty();
    }

    @Test
    @DisplayName("여러 번 부르면 서로 다른 미션이 나온다 — 매번 같은 것만 주지 않는다")
    void picksRandomly() {
        savedMission("기존");
        savedMission("기존");
        savedMission("기존");
        MissionMatcher matcher = matcherWith(new RecordingGenerator());

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            matcher.matchFor(place).ifPresent(mission -> seen.add(mission.getTitle()));
        }

        assertThat(seen).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("다른 장소의 미션은 섞이지 않는다")
    void doesNotBorrowFromOtherPlaces() {
        Place other = placeRepository.save(Place.builder()
                .contentId("place-2")
                .contentTypeId("12")
                .name("경포해변")
                .category(TravelCategory.NATURE_ATTRACTION)
                .latitude(37.7956)
                .longitude(128.9089)
                .build());
        missionRepository.save(Mission.create(other, "남의 미션", "설명", null, 100));

        Optional<Mission> picked = matcherWith(new RecordingGenerator()).matchFor(place);

        assertThat(picked).isPresent();
        assertThat(picked.get().getTitle()).isNotEqualTo("남의 미션");
        assertThat(picked.get().getPlace().getId()).isEqualTo(place.getId());
    }
}
