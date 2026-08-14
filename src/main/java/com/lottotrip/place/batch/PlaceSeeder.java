package com.lottotrip.place.batch;

import com.lottotrip.common.enums.MediaType;
import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.PlaceMedia;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.PlaceMediaRepository;
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
 * <p>대표 이미지({@code firstimage})도 함께 담는다(6-6에서 확정). 목록 응답에만 딸려 오는 값이라
 * 지금 담지 않으면 나중에 채우려 할 때 전체를 다시 받아야 한다. 추가 API 호출은 없다.
 * 실행은 {@code SeedRunner}가 맡는다 — {@code SEED_ON_STARTUP=true}인 실행에서만 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceSeeder {

    /**
     * 한 번의 적재에서 넘길 수 있는 최대 페이지 수. <b>폭주를 막는 마지막 방어선이다.</b>
     *
     * <p>2026-08-14에 실제로 사고가 났다. 범위를 넘어선 페이지의 응답이
     * {@code numOfRows: 0}으로 오는 것을 몰라 {@code hasNext()}가 영원히 참이 되었고,
     * 이 루프가 <b>일일 할당량 1,000회를 통째로 태우고서야</b> 429로 멈췄다.
     * 게다가 이 메서드는 트랜잭션 하나로 묶여 있어, 그렇게 받은 것마저 전부 롤백됐다.
     *
     * <p>{@code hasNext()}는 고쳤지만 상한을 함께 둔다. <b>종료 조건이 하나뿐이면
     * 그것이 틀렸을 때 막아 줄 것이 없다.</b> 바깥 API의 응답 형태는 우리가 정하지 못한다.
     *
     * <p>200페이지 × 100건 = 20,000건. 강원 전체가 2,373건(실측)이므로 넉넉하다.
     * 여기 걸린다면 정상 상황이 아니므로 경고를 남긴다.
     */
    static final int MAX_PAGES = 200;

    private final TourApiClient tourApiClient;
    private final PlaceRepository placeRepository;
    private final PlaceMediaRepository placeMediaRepository;
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

            // 빈 페이지는 그 자체로 끝이라는 신호다. 전체 건수와 어긋나더라도 여기서 멈춘다.
            // 이 검사가 없으면 "더 있다"고 말하면서 아무것도 안 주는 응답을 영원히 받아 간다.
            if (page.items().isEmpty()) {
                break;
            }

            for (TourApiPlaceItem item : page.items()) {
                if (upsert(item, cityCache)) {
                    saved++;
                } else {
                    skipped++;
                }
            }

            // 다음 페이지가 없으면 끝. 한 페이지만 받고 멈추면 강원 2,373건 중 100건만 담긴다.
            if (!page.hasNext()) {
                break;
            }
            if (pageNo >= MAX_PAGES) {
                log.warn("페이지 상한 {}에 도달해 적재를 중단합니다 — 지역 {} / 전체 {}건 중 일부만 담겼습니다."
                                + " 응답이 끝을 알리지 않고 있을 수 있습니다",
                        MAX_PAGES, areaCode, page.totalCount());
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

        Place saved = placeRepository.findByContentId(item.contentId())
                .map(existing -> {
                    existing.updateFrom(fresh);
                    return existing;
                })
                .orElseGet(() -> placeRepository.save(fresh));

        saveThumbnail(saved, item.thumbnailUrl());
        return true;
    }

    /**
     * 대표 이미지를 담는다. 슬롯 응답의 {@code thumbnailUrl}이 여기서 나온다. (6-6)
     *
     * <p><b>지금 담아 두지 않으면 나중에 채울 방법이 없다.</b> 이미지 주소는 목록 응답에만 딸려 오므로,
     * 뒤늦게 필요해지면 전체를 다시 받아야 한다. 담는 비용은 이미 받은 값을 저장하는 것뿐이라
     * 추가 API 호출이 없다.
     *
     * <p>⚠️ 실측 채움률은 <b>18%</b>다. 대부분의 장소에는 이미지가 없고, 그 경우 행을 만들지 않는다.
     * 빈 문자열로 행을 만들면 {@code media_url}이 NOT NULL인데도 의미 없는 값이 쌓이고
     * 조회할 때 빈 URL이 그대로 응답에 나간다.
     */
    private void saveThumbnail(Place place, String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return;
        }
        // 적재는 여러 번 돌아간다. 막지 않으면 돌릴 때마다 같은 이미지 행이 하나씩 늘어난다.
        if (placeMediaRepository.existsByPlaceIdAndMediaUrl(place.getId(), thumbnailUrl)) {
            return;
        }
        placeMediaRepository.save(PlaceMedia.create(place, thumbnailUrl, MediaType.IMAGE));
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
