package com.lottotrip.mission.service;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 임시 미션 생성기 검증. (roadmap 6-5)
 *
 * ⚠️ **이 구현체는 회의 결과가 나오면 교체된다.** 생성 방식(LLM vs 키워드·템플릿)이
 * 보류 상태라, 그때까지 6-6 draw가 실제로 굴러가도록 자리를 채우는 최소 구현이다.
 * 여기서 검증하는 것은 문구의 품질이 아니라 **계약**이다 — 요청한 개수를 주는가,
 * 저장할 수 있는 형태인가(`title`·`reward_point`가 채워졌는가).
 */
class TemplateMissionGeneratorTest {

    private static final Place PLACE = Place.builder()
            .contentId("c-1")
            .contentTypeId("12")
            .name("사천진해변")
            .category(TravelCategory.NATURE_ATTRACTION)
            .latitude(37.8021)
            .longitude(128.8954)
            .build();

    private final MissionGenerator generator = new TemplateMissionGenerator();

    @Test
    @DisplayName("요청한 개수만큼 만든다")
    void generatesRequestedCount() {
        assertThat(generator.generate(PLACE, 3)).hasSize(3);
        assertThat(generator.generate(PLACE, 1)).hasSize(1);
    }

    @Test
    @DisplayName("0개나 음수를 요청하면 빈 목록이다")
    void generatesNothingForNonPositiveCount() {
        assertThat(generator.generate(PLACE, 0)).isEmpty();
        assertThat(generator.generate(PLACE, -1)).isEmpty();
    }

    @Test
    @DisplayName("저장할 수 있는 형태로 만든다 — title과 reward_point는 NOT NULL이다")
    void producesPersistableMissions() {
        List<Mission> missions = generator.generate(PLACE, 3);

        assertThat(missions).allSatisfy(mission -> {
            assertThat(mission.getTitle()).isNotBlank();
            assertThat(mission.getRewardPoint()).isNotNull().isPositive();
            assertThat(mission.getPlace()).isEqualTo(PLACE);
        });
    }

    @Test
    @DisplayName("한 장소 안에서 제목이 겹치지 않는다")
    void producesDistinctTitles() {
        // 같은 제목이 여러 개면 사용자에게 같은 미션이 반복해 보인다.
        // 나중에 (place_id, title) UNIQUE를 걸기로 하면 저장 자체가 실패하기도 한다.
        List<Mission> missions = generator.generate(PLACE, 3);

        assertThat(missions).extracting(Mission::getTitle).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("장소 이름이 문구에 들어간다")
    void mentionsThePlaceName() {
        List<Mission> missions = generator.generate(PLACE, 3);

        assertThat(missions).allSatisfy(mission ->
                assertThat(mission.getTitle()).contains("사천진해변"));
    }

    @Test
    @DisplayName("템플릿 개수보다 많이 요청하면 있는 만큼만 준다")
    void stopsAtTemplateCount() {
        // 억지로 채우려면 같은 문구에 번호를 덧붙이는 수밖에 없는데,
        // 그러면 "사천진해변에 도착해 인증하기 (2)" 같은 사람이 쓰지 않을 문구가 사용자에게 나간다.
        // 부르는 쪽(MissionMatcher)이 "요청보다 적게 받는 것"을 이미 허용하므로 적게 주는 편이 낫다.
        List<Mission> missions = generator.generate(PLACE, 12);

        assertThat(missions).hasSize(4)
                .extracting(Mission::getTitle).doesNotHaveDuplicates();
    }
}
