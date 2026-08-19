package com.lottotrip.place.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 지역 시드를 돌리는 수동 진입점. (roadmap 5-8, 6-14)
 *
 * ⚠️ **예전에는 장소 적재도 함께 돌렸다.** 결정 12(온디맨드)로 배치 적재가 폐기되면서
 * `PlaceSeeder`가 사라져 **지역 시드만 남았다.** 장소는 이제 뽑힐 때마다
 * `PlaceUpserter`가 한 건씩 담는다.
 *
 * **지역 표는 여전히 필요하다.** `places.city_id`를 채우려면 `states`·`cities`가
 * 있어야 하는데, 이건 TourAPI에서 받아야 얻을 수 있다. 다만 호출이 2회로 끝나고
 * 지역 정보는 거의 바뀌지 않아 **사람이 필요할 때 한 번 돌리는** 방식 그대로 둔다.
 *
 * **평소에는 존재하지 않는다.** `tourapi.seed-on-startup=true`로 켠 실행에서만
 * 빈이 만들어진다. 켜는 방법은 이렇다.
 *
 * ```
 * SEED_ON_STARTUP=true ./gradlew bootRun
 * ```
 *
 * **왜 조건부 빈인가.** 실행 시점에 `if (enabled)`로 거르는 방법도 있지만,
 * 그러면 빈은 존재하므로 누군가 직접 부를 수 있고 테스트 컨텍스트에도 딸려 온다.
 * **빈을 아예 만들지 않으면 부를 방법 자체가 없다.**
 *
 * **왜 기동 시 자동 실행이 아닌가.** 서버를 띄울 때마다 돌면 개발 중에 할당량이 조용히 깎이고,
 * 테스트를 돌릴 때도 바깥으로 요청이 나간다. 지역 데이터는 거의 바뀌지 않으므로 자동으로 돌 이유가 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tourapi.seed-on-startup", havingValue = "true")
public class SeedRunner implements CommandLineRunner {

    private final RegionSeeder regionSeeder;
    private final String areaCode;

    public SeedRunner(RegionSeeder regionSeeder,
                      @Value("${tourapi.seed-area-code:32}") String areaCode) {
        this.regionSeeder = regionSeeder;
        this.areaCode = areaCode;
    }

    /**
     * 시도·시군구 표를 채운다.
     *
     * **슬롯을 돌리기 전에 한 번 돌려 두어야 한다.** 지역 표가 비어 있어도 슬롯은 동작하지만
     * (`city_id`가 nullable이다) 뽑힌 장소가 어느 시·군인지 알 수 없게 된다.
     */
    @Override
    public void run(String... args) {
        log.info("지역 시드를 시작합니다 — 지역 코드 {}", areaCode);
        regionSeeder.seed(areaCode);
        log.info("지역 시드를 마쳤습니다 — 지역 코드 {}", areaCode);
    }
}
