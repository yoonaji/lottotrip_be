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
 * TourAPI 지역코드를 {@code states}·{@code cities}에 시드한다. (roadmap 5-8, 결정 10)
 *
 * <p><b>왜 필요한가.</b> TourAPI는 장소의 지역을 <b>코드로만</b> 준다({@code areacode}·{@code sigungucode}).
 * 이름은 주지 않는다. 그래서 코드와 우리 지역 행을 미리 이어 두지 않으면,
 * 장소를 적재할 때(5-9) {@code places.city_id}를 채울 방법이 없다.
 *
 * <p><b>호출은 2회뿐이다.</b> 시도 목록 1회 + 대상 시도의 시군구 1회.
 * 전국 시군구를 다 받으면 18회가 되는데, 적재 범위가 강원 한정이라 나머지는 쓰이지 않는다.
 *
 * <p><b>여러 번 돌려도 안전하다.</b> 지역이 이미 있으면 새로 만들지 않고 이름만 맞춘다.
 * 시드는 한 번으로 끝나지 않는다 — 적재가 실패해 다시 돌리거나, 지역이 늘어 다시 받는 경우가 있다.
 *
 * <p>⚠️ <b>실행 시점(수동 1회 / 스케줄러 / 기동 시)은 아직 정하지 않았다.</b> (5-9에서 정한다)
 * 지금은 불러 주면 도는 부품이고, 어디서 부를지는 붙이지 않았다.
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
     * @param targetAreaCode 시군구까지 받을 시도 코드. 강원은 {@code "32"}
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
     * <p><b>이름이 아니라 코드로 찾는 이유:</b> TourAPI가 주는 이름은 {@code "강원"}인데
     * 우리가 쓰던 이름은 {@code "강원특별자치도"}처럼 다를 수 있다. 이름으로 맞추려 하면
     * 같은 지역을 못 알아보고 행을 하나 더 만든다. 코드는 API가 보증하는 식별자다.
     */
    private void upsertState(TourApiAreaCodeItem item) {
        stateRepository.findByTourAreaCode(item.code())
                .ifPresentOrElse(
                        state -> state.rename(item.name()),
                        () -> stateRepository.save(State.create(item.name(), item.code())));
    }

    /**
     * 시군구도 같은 방식이되 <b>시도와 코드를 함께</b> 본다.
     *
     * <p>시군구 코드는 시·도 안에서만 유일하다. 강원의 {@code "1"}은 강릉시, 서울의 {@code "1"}은 강남구다.
     * 코드만으로 찾으면 다른 시도의 시·군을 같은 것으로 착각해 덮어쓴다.
     */
    private void upsertCity(State state, TourApiAreaCodeItem item) {
        cityRepository.findByStateIdAndTourSigunguCode(state.getId(), item.code())
                .ifPresentOrElse(
                        city -> city.rename(item.name()),
                        () -> cityRepository.save(City.create(state, item.name(), item.code())));
    }
}
