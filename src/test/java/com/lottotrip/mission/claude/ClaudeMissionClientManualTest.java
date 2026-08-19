package com.lottotrip.mission.claude;

import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 Claude API를 한 번 불러 보는 수동 확인. (roadmap 8-5)
 *
 * **평소 빌드에서는 실행되지 않는다.** `build.gradle`의 `test` 작업에서
 * `manual` 태그를 제외해 뒀다. 돌리려면:
 *
 * ```
 * ANTHROPIC_API_KEY=sk-ant-... ./gradlew manualTest --tests '*ClaudeMissionClientManualTest'
 * ```
 *
 * **왜 떼어 놨는가** — 이 테스트는 네트워크·키·요금·모델의 그날 응답에 의존한다.
 * 평소 빌드에 섞여 있으면 우리 코드가 멀쩡한데도 빨간 불이 뜨고, 그 빨간 불이 반복되면
 * 아무도 테스트 결과를 믿지 않게 된다. 자동 검증은 `ClaudeMissionGeneratorTest`가 하고,
 * 여기서는 **"진짜로 연결되는가"**만 사람이 확인한다.
 */
@Tag("manual")
class ClaudeMissionClientManualTest {

    private static final Place PLACE = Place.builder()
            .contentId("c-1")
            .contentTypeId("12")
            .name("사천진해변")
            .category(TravelCategory.NATURE_ATTRACTION)
            .latitude(37.8021)
            .longitude(128.8954)
            .build();

    private ClaudeMissionClient client;

    @BeforeEach
    void setUp() {
        // 스프링을 띄우지 않으므로 환경변수를 직접 읽는다.
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "ANTHROPIC_API_KEY가 없어 건너뜁니다");

        client = new ClaudeMissionClient(new AnthropicProperties(apiKey, null, 0, 20));
    }

    @Test
    @DisplayName("실제로 미션 문구 3개를 받아 온다")
    void writesRealCopies() {
        List<MissionCopy> copies = client.write(PLACE, 3);

        copies.forEach(copy -> System.out.println("- " + copy.title() + " / " + copy.description()));

        assertThat(copies).hasSize(3);
        assertThat(copies).allSatisfy(copy -> {
            assertThat(copy.title()).isNotBlank();
            // 장소 이름이 들어가라고 지시했다. 안 들어가면 프롬프트를 다시 봐야 한다.
            assertThat(copy.title()).contains("사천진");
        });
    }
}
