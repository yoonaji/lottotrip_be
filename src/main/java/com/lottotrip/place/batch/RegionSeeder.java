package com.lottotrip.place.batch;

import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.place.tourapi.TourApiAreaCodeItem;
import com.lottotrip.place.tourapi.TourApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TourAPI 지역코드를 `states`·`cities`에 시드한다. (roadmap 5-8)
 *
 * **결정 12(배치 폐기) 후에도 살아남은 유일한 배치다.** 장소 적재 파이프라인이 아니라
 * **코드↔지역명 사전**이라, `PlaceUpserter.resolveCity()`가 그대로 쓴다.
 *
 * **왜 필요한가.** TourAPI는 장소의 지역을 **코드로만** 준다(`areacode`·`sigungucode`).
 * 이름은 주지 않는다. 그래서 코드와 우리 지역 행을 미리 이어 두지 않으면
 * `places.city_id`를 채울 방법이 없다.
 *
 * **호출은 2회뿐이다.** 시도 목록 1회 + 대상 시도의 시군구 1회.
 * ⚠️ 결정 12로 **서비스 범위가 전국이 됐다.** 대상 시도 밖에서 뽑힌 장소는 시군구 행이 없어
 * `city_id`가 NULL로 남는다. 필요해지면 시도별로 다시 돌리면 된다.
 *
 * **여러 번 돌려도 안전하다.** 지역이 이미 있으면 새로 만들지 않고 이름만 맞춘다.
 *
 * 실행은 {@link SeedRunner}가 `tourapi.seed-on-startup=true`일 때만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionSeeder {

    private final TourApiClient tourApiClient;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;

    /**
     * 시도 전체와 대상 시도의 시군구를 시드한다.
     *
     * @param targetAreaCode 시군구까지 받을 시도 코드. 강원은 `"32"`
     */
    @Transactional
    public void seed(String targetAreaCode) {
        List<TourApiAreaCodeItem> states = tourApiClient.fetchAreaCodes(null);
        states.forEach(this::upsertState);
        log.info("시도 시드 완료 — {}건", states.size());

        // 시도를 먼저 전부 넣는 이유: 대상 시도의 행이 있어야 시군구를 매달 수 있고,
        // 시도 목록은 어차피 한 번의 호출로 전부 오므로 골라 담을 이유가 없다.
        State target = stateRepository.findByTourAreaCode(targetAreaCode)
                .orElseThrow(() -> new IllegalStateException(
                        "TourAPI 시도 목록에 대상 코드가 없습니다: " + targetAreaCode));

        List<TourApiAreaCodeItem> cities = tourApiClient.fetchAreaCodes(Integer.parseInt(targetAreaCode));
        cities.forEach(city -> upsertCity(target, city));
        log.info("시군구 시드 완료 — {} {}건", target.getStateName(), cities.size());
    }

    /**
     * 코드로 찾아 없으면 만들고, 있으면 이름만 맞춘다.
     *
     * **이름이 아니라 코드로 찾는 이유:** TourAPI가 주는 이름은 `"강원"`인데
     * 우리가 쓰던 이름은 `"강원특별자치도"`처럼 다를 수 있다. 이름으로 맞추려 하면
     * 같은 지역을 못 알아보고 행을 하나 더 만든다. 코드는 API가 보증하는 식별자다.
     */
    private void upsertState(TourApiAreaCodeItem item) {
        stateRepository.findByTourAreaCode(item.code())
                .ifPresentOrElse(
                        state -> state.rename(item.name()),
                        () -> stateRepository.save(State.create(item.name(), item.code())));
    }

    /**
     * 시군구도 같은 방식이되 **시도와 코드를 함께** 본다.
     *
     * 시군구 코드는 시·도 안에서만 유일하다. 강원의 `"1"`은 강릉시, 서울의 `"1"`은 강남구다.
     * 코드만으로 찾으면 다른 시도의 시·군을 같은 것으로 착각해 덮어쓴다.
     */
    private void upsertCity(State state, TourApiAreaCodeItem item) {
        cityRepository.findByStateIdAndTourSigunguCode(state.getId(), item.code())
                .ifPresentOrElse(
                        city -> city.rename(item.name()),
                        () -> cityRepository.save(City.create(state, item.name(), item.code())));
    }
}
