package com.lottotrip.place.batch;

import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiPage;
import com.lottotrip.place.tourapi.TourApiPlaceItem;
import com.lottotrip.place.tourapi.TravelCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * TourAPI 장소를 {@code places}에 적재한다. (roadmap 5-9, 결정 10)
 *
 * <p><b>슬롯 추첨의 후보가 전부 여기서 나온다.</b> 결정 10으로 draw는 DB만 보므로,
 * 이 배치가 담지 않은 장소는 사용자에게 영영 뽑히지 않는다.
 *
 * <p><b>여러 번 돌려도 안전하다.</b> {@code content_id}로 찾아 있으면 갱신한다.
 * 적재는 한 번에 끝나지 않는다 — 중간에 실패해 다시 돌리거나, 정보가 바뀌어 다시 받는 경우가 있다.
 *
 * <h2>건너뛰는 것</h2>
 * <ul>
 *   <li><b>여행지 3종이 아닌 것</b> — 숙박·음식점·쇼핑. 필터가 없으면 모텔이 여행지로 뽑힌다</li>
 *   <li><b>좌표가 없는 것</b> — {@code latitude}·{@code longitude}가 NOT NULL이라 넣으면 터지고,
 *       넣는다 해도 반경 검색에 걸리지 않아 쓸모가 없다</li>
 * </ul>
 *
 * <p>⚠️ <b>실행 시점(수동 / 스케줄러 / 기동 시)은 아직 붙이지 않았다.</b>
 * 대표 이미지({@code place_media})도 담지 않는다 — 빈 경우의 처리 방침이 미확정이라 6-6에서 정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceSeeder {

    private final TourApiClient tourApiClient;
    private final PlaceRepository placeRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final TravelCategoryMapper categoryMapper;

    /**
     * 한 시도의 장소를 전부 적재한다.
     *
     * @param areaCode TourAPI 시도 코드. 강원은 {@code "32"}
     */
    @Transactional
    public void seed(String areaCode) {
        Map<String, Optional<City>> cityCache = new HashMap<>();
        int saved = 0;
        int skipped = 0;
        int pageNo = 1;

        while (true) {
            TourApiPage<TourApiPlaceItem> page =
                    tourApiClient.fetchAreaBasedList(Integer.parseInt(areaCode), pageNo);

            for (TourApiPlaceItem item : page.items()) {
                if (upsert(item, cityCache)) {
                    saved++;
                } else {
                    skipped++;
                }
            }

            // 다음 페이지가 없으면 끝. 한 페이지만 받고 멈추면 강원 2,600건 중 100건만 담긴다.
            if (!page.hasNext()) {
                break;
            }
            pageNo++;
        }

        log.info("장소 적재 완료 — 지역 {} / 담음 {}건 / 건너뜀 {}건", areaCode, saved, skipped);
    }

    /**
     * 한 건을 담거나 갱신한다.
     *
     * @return 담았으면 true, 건너뛰었으면 false
     */
    private boolean upsert(TourApiPlaceItem item, Map<String, Optional<City>> cityCache) {
        if (!categoryMapper.isTargetContentType(item.contentTypeId())) {
            return false;
        }
        // 좌표가 없으면 저장 자체가 실패한다. 한 건 때문에 배치가 멈추지 않도록 여기서 거른다.
        if (item.latitude() == null || item.longitude() == null) {
            log.debug("좌표가 없어 건너뜀 — {} ({})", item.title(), item.contentId());
            return false;
        }

        Place fresh = Place.builder()
                .contentId(item.contentId())
                .contentTypeId(item.contentTypeId())
                .city(resolveCity(item, cityCache).orElse(null))
                .name(item.title())
                .category(categoryMapper.map(
                        item.contentTypeId(), item.cat1(), item.cat2(), item.cat3()))
                .address(item.address())
                .latitude(item.latitude())
                .longitude(item.longitude())
                .modifiedTime(item.modifiedDateTime())
                .build();

        placeRepository.findByContentId(item.contentId())
                .ifPresentOrElse(
                        existing -> existing.updateFrom(fresh),
                        () -> placeRepository.save(fresh));
        return true;
    }

    /**
     * 지역 코드로 시·군을 찾는다. 5-8 시드가 만들어 둔 표를 본다.
     *
     * <p><b>못 찾아도 장소는 담는다.</b> 여기서 멈추면 지역 시드가 하루 늦어질 때 적재 전체가 묶인다.
     * {@code places.city_id}를 nullable로 둔 이유가 이것이다.
     *
     * <p>결과를 {@code cityCache}에 모아 두는 이유: 강원 장소 1,241건이 모두 같은 시·군 몇 개에
     * 몰려 있어, 캐시가 없으면 <b>거의 같은 조회를 1,241번</b> 하게 된다.
     */
    private Optional<City> resolveCity(TourApiPlaceItem item, Map<String, Optional<City>> cityCache) {
        if (item.areaCode() == null || item.sigunguCode() == null) {
            return Optional.empty();
        }
        String key = item.areaCode() + ":" + item.sigunguCode();
        return cityCache.computeIfAbsent(key, ignored -> lookUpCity(item));
    }

    private Optional<City> lookUpCity(TourApiPlaceItem item) {
        Optional<State> state = stateRepository.findByTourAreaCode(item.areaCode());
        if (state.isEmpty()) {
            log.warn("시도를 찾지 못했습니다 — areaCode {}. 5-8 지역 시드를 먼저 돌려야 합니다", item.areaCode());
            return Optional.empty();
        }
        // 시군구 코드는 시도 안에서만 유일하다. 시도를 함께 넘기지 않으면 다른 지역의 시·군이 걸린다.
        return cityRepository.findByStateIdAndTourSigunguCode(state.get().getId(), item.sigunguCode());
    }
}
