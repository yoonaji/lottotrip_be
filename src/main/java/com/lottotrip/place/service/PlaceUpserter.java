package com.lottotrip.place.service;

import com.lottotrip.common.enums.MediaType;
import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.PlaceMedia;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.place.tourapi.TourApiPlaceItem;
import com.lottotrip.place.tourapi.TravelCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 뽑힌 장소 하나를 {@code places}에 담는다. (roadmap 6-12, 결정 12)
 *
 * <p>배치가 강원 전역을 미리 담던 자리를 대신한다. 온디맨드에서는 <b>추첨된 1건만</b> 저장하므로,
 * 여기서 실패하면 그 장소는 사용자에게 나가지 못한다.
 *
 * <p><b>왜 저장하는가.</b> 슬롯 결과({@code saved_slots})와 미션({@code missions})이 모두
 * {@code place_id}를 가리킨다. 뽑기만 하고 담지 않으면 <b>이어 붙일 곳이 없다.</b>
 * 나중에 결과를 다시 조회할 때 {@code content_id}로 실시간 세부 정보도 받아 온다.
 *
 * <p><b>여러 번 뽑혀도 안전하다.</b> {@code content_id}로 찾아 있으면 갱신한다. 온디맨드에서는
 * 인기 있는 장소가 반복해서 뽑히므로, 막지 않으면 같은 곳이 계속 쌓인다.
 *
 * <p>⚠️ <b>배치의 {@code PlaceSeeder}에서 옮겨 온 로직이다.</b> 검증된 코드라 그대로 가져왔고,
 * 다만 <b>시·군 조회 캐시는 뺐다.</b> 배치는 1,241건을 한 번에 훑어 캐시가 필요했지만
 * 여기는 요청당 1건이라 캐시가 오히려 오래된 값을 붙들 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceUpserter {

    private final PlaceRepository placeRepository;
    private final PlaceMediaRepository placeMediaRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final TravelCategoryMapper categoryMapper;

    /**
     * 한 건을 담거나 갱신하고, 저장된 장소를 돌려준다.
     *
     * <p>돌려주는 이유: 부르는 쪽(6-13)이 이 {@code Place}로 {@code saved_slots}를 만들고
     * 미션을 붙여야 한다. {@code void}면 다시 조회해야 한다.
     *
     * @throws IllegalArgumentException 좌표가 없을 때. 아래 설명 참조
     */
    @Transactional
    public Place upsert(TourApiPlaceItem item) {
        requireCoordinate(item);

        Place fresh = Place.builder()
                .contentId(item.contentId())
                .contentTypeId(item.contentTypeId())
                .city(resolveCity(item).orElse(null))
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
        return saved;
    }

    /**
     * 좌표가 없으면 담지 않는다.
     *
     * <p>{@code latitude}·{@code longitude}는 NOT NULL이라 넣으면 저장이 실패한다.
     *
     * <p><b>배치와 다르게 예외를 던진다.</b> 배치는 수천 건을 훑으며 한 건 때문에 멈추면 안 되므로
     * 조용히 건너뛰었다. 여기는 <b>이미 뽑힌 1건</b>이라 건너뛸 것이 없다.
     * 게다가 좌표 기반으로 조회해 받은 결과이므로 좌표가 없을 수 없다 —
     * 그런데도 여기 왔다면 <b>선정기가 걸러야 할 것을 놓친 우리 코드의 버그</b>다.
     */
    private void requireCoordinate(TourApiPlaceItem item) {
        if (item.latitude() == null || item.longitude() == null) {
            throw new IllegalArgumentException(
                    "좌표가 없는 장소는 담을 수 없습니다: " + item.title() + " (" + item.contentId() + ")");
        }
    }

    /**
     * 대표 이미지를 담는다. 슬롯 응답의 {@code thumbnailUrl}이 여기서 나온다.
     *
     * <p><b>지금 담아 두지 않으면 나중에 채울 방법이 없다.</b> 이미지 주소는 목록 응답에만 딸려 오므로,
     * 뒤늦게 필요해지면 그 장소를 다시 받아야 한다. 담는 비용은 이미 받은 값을 저장하는 것뿐이다.
     *
     * <p>없으면 행을 만들지 않는다. 빈 문자열로 만들면 {@code media_url}이 NOT NULL인데도
     * 의미 없는 값이 쌓이고, 조회할 때 빈 URL이 그대로 응답에 나간다.
     */
    private void saveThumbnail(Place place, String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return;
        }
        // 같은 장소가 다시 뽑힐 때마다 같은 이미지 행이 하나씩 늘어나는 것을 막는다.
        if (placeMediaRepository.existsByPlaceIdAndMediaUrl(place.getId(), thumbnailUrl)) {
            return;
        }
        placeMediaRepository.save(PlaceMedia.create(place, thumbnailUrl, MediaType.IMAGE));
    }

    /**
     * 지역 코드로 시·군을 찾는다. 5-8 지역 시드가 만들어 둔 표를 본다.
     *
     * <p><b>못 찾아도 장소는 담는다.</b> 여기서 멈추면 지역 표가 비어 있는 동안 슬롯 전체가 죽는다.
     * {@code places.city_id}를 nullable로 둔 이유가 이것이다.
     */
    private Optional<City> resolveCity(TourApiPlaceItem item) {
        if (item.areaCode() == null || item.sigunguCode() == null) {
            return Optional.empty();
        }
        Optional<State> state = stateRepository.findByTourAreaCode(item.areaCode());
        if (state.isEmpty()) {
            log.warn("시도를 찾지 못했습니다 — areaCode {}. 지역 시드(5-8)를 먼저 돌려야 합니다", item.areaCode());
            return Optional.empty();
        }
        // 시군구 코드는 시도 안에서만 유일하다. 시도를 함께 넘기지 않으면 다른 지역의 시·군이 걸린다.
        return cityRepository.findByStateIdAndTourSigunguCode(state.get().getId(), item.sigunguCode());
    }
}
