package com.lottotrip.place.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드 진입점 검증. (roadmap 5-6, 5-9 실행 방식)
 *
 * <p><b>평소에는 돌지 않는다.</b> 설정값을 켠 실행에서만 동작한다.
 * 기동할 때마다 자동으로 돌면 개발 중에 공공 API 할당량이 조용히 깎이고,
 * 테스트를 돌릴 때도 바깥으로 요청이 나간다.
 */
class SeedRunnerTest {

    /** 어느 시드가 어떤 순서로 불렸는지만 적어 두는 가짜. */
    private static class Recorder {
        final List<String> calls = new ArrayList<>();
    }

    private static RegionSeeder fakeRegionSeeder(Recorder recorder) {
        return new RegionSeeder(null, null, null) {
            @Override
            public void seed(String areaCode) {
                recorder.calls.add("region:" + areaCode);
            }
        };
    }

    private static PlaceSeeder fakePlaceSeeder(Recorder recorder) {
        return new PlaceSeeder(null, null, null, null, null) {
            @Override
            public void seed(String areaCode) {
                recorder.calls.add("place:" + areaCode);
            }
        };
    }

    @Test
    @DisplayName("지역을 먼저 시드하고 그다음 장소를 적재한다")
    void seedsRegionBeforePlaces() throws Exception {
        // ⚠️ 순서가 중요하다. 장소 적재는 areacode·sigungucode로 시·군을 찾는데,
        // 지역 시드가 먼저 돌지 않으면 표가 비어 있어 places.city_id가 전부 NULL이 된다.
        // 적재가 멈추지는 않아서(의도된 동작) 순서를 틀려도 조용히 지나간다.
        Recorder recorder = new Recorder();
        SeedRunner runner = new SeedRunner(
                fakeRegionSeeder(recorder), fakePlaceSeeder(recorder), "32");

        runner.run();

        assertThat(recorder.calls).containsExactly("region:32", "place:32");
    }

    @Test
    @DisplayName("설정한 지역 코드를 두 시드에 그대로 넘긴다")
    void passesConfiguredAreaCode() throws Exception {
        Recorder recorder = new Recorder();
        SeedRunner runner = new SeedRunner(
                fakeRegionSeeder(recorder), fakePlaceSeeder(recorder), "39");

        runner.run();

        assertThat(recorder.calls).containsExactly("region:39", "place:39");
    }

    // ---------- 평소에는 빈이 아예 만들어지지 않는다 ----------

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withBean(RegionSeeder.class, () -> fakeRegionSeeder(new Recorder()))
            .withBean(PlaceSeeder.class, () -> fakePlaceSeeder(new Recorder()))
            .withUserConfiguration(SeedRunner.class);

    @Test
    @DisplayName("설정이 없으면 진입점이 아예 만들어지지 않는다")
    void isAbsentByDefault() {
        // 조건을 "빈을 만들지 말지"로 두면 실행 시점에 if로 거르는 것보다 확실하다.
        // 빈이 없으면 부를 방법 자체가 없다.
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(SeedRunner.class));
    }

    @Test
    @DisplayName("설정을 켜면 진입점이 만들어진다")
    void isPresentWhenEnabled() {
        contextRunner
                .withPropertyValues("tourapi.seed-on-startup=true")
                .run(context -> assertThat(context).hasSingleBean(SeedRunner.class));
    }

    @Test
    @DisplayName("설정을 false로 명시해도 만들어지지 않는다")
    void isAbsentWhenExplicitlyDisabled() {
        contextRunner
                .withPropertyValues("tourapi.seed-on-startup=false")
                .run(context -> assertThat(context).doesNotHaveBean(SeedRunner.class));
    }
}
