package com.lottotrip.place.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 지역·장소 시드를 한 번에 돌리는 수동 진입점. (roadmap 5-6, 5-9 실행 방식)
 *
 * <p><b>평소에는 존재하지 않는다.</b> {@code tourapi.seed-on-startup=true}로 켠 실행에서만
 * 빈이 만들어진다. 켜는 방법은 이렇다.
 *
 * <pre>
 * SEED_ON_STARTUP=true ./gradlew bootRun
 * </pre>
 *
 * <p><b>왜 조건부 빈인가.</b> 실행 시점에 {@code if (enabled)}로 거르는 방법도 있지만,
 * 그러면 빈은 존재하므로 누군가 직접 부를 수 있고 테스트 컨텍스트에도 딸려 온다.
 * <b>빈을 아예 만들지 않으면 부를 방법 자체가 없다.</b>
 *
 * <p><b>왜 기동 시 자동 실행이 아닌가.</b> 적재는 공공 API를 수백~수천 번 부른다.
 * 서버를 띄울 때마다 돌면 개발 중에 <b>할당량이 조용히 깎이고</b>, 테스트를 돌릴 때도 바깥으로 요청이 나간다.
 * 지역 데이터는 거의 바뀌지 않으므로 자동으로 돌 이유도 없다.
 *
 * <p>⚠️ <b>정기 갱신은 아직 없다.</b> 지금은 "필요할 때 사람이 켜서 돌리는" 것까지다.
 * 갱신 주기를 정하면 {@code modified_time}을 비교해 바뀐 것만 다시 받는 방식으로 붙인다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tourapi.seed-on-startup", havingValue = "true")
public class SeedRunner implements CommandLineRunner {

    private final RegionSeeder regionSeeder;
    private final PlaceSeeder placeSeeder;
    private final String areaCode;

    public SeedRunner(RegionSeeder regionSeeder,
                      PlaceSeeder placeSeeder,
                      @Value("${tourapi.seed-area-code:32}") String areaCode) {
        this.regionSeeder = regionSeeder;
        this.placeSeeder = placeSeeder;
        this.areaCode = areaCode;
    }

    /**
     * 지역을 먼저, 장소를 나중에 시드한다.
     *
     * <p><b>순서가 중요하다.</b> 장소 적재는 {@code areacode}·{@code sigungucode}로 시·군을 찾는데,
     * 지역 표가 비어 있으면 {@code places.city_id}가 전부 NULL이 된다.
     * 적재가 멈추지는 않으므로(의도된 동작) 순서를 틀려도 조용히 지나간다 — 그래서 여기서 고정한다.
     */
    @Override
    public void run(String... args) {
        log.info("시드를 시작합니다 — 지역 코드 {}", areaCode);
        regionSeeder.seed(areaCode);
        placeSeeder.seed(areaCode);
        log.info("시드를 마쳤습니다 — 지역 코드 {}", areaCode);
    }
}
