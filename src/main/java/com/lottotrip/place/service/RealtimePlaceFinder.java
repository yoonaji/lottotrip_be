package com.lottotrip.place.service;

import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiPlaceItem;
import com.lottotrip.place.tourapi.TourApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * 반경 안의 장소를 실시간으로 받아 그중 하나를 뽑는다. (roadmap 6-11, 결정 12)
 *
 * 슬롯 추첨의 전부가 여기서 일어난다. 사용자에게 전송되는 장소는
 * 이 클래스가 어떤 후보를 받아 오고 그중 무엇을 고르는가로 정해진다.
 *
 * 페이지를 넘기지 않는다. 2026-08-14에 페이지 순회가 끝나지 않아 할당량 1,000회를
 * 통째로 태운 적이 있다. 순회 자체를 하지 않으면 그 사고가 구조적으로 불가능해진다.
 *
 * 받은 후보를 그대로 쓰지 않는다. **좌표가 없는 것과 숙박은 뽑기 전에 뺀다**(결정 18).
 * 숙소는 온보딩에서 따로 입력받으므로 여행지 후보가 아니다.
 *
 * 한계(참고) — 후보 1,000건까지만 닿는다
 * `numOfRows`의 API 상한이 1,000이다. 그보다 많으면 `arrange=E`(거리순) 덕에
 * 먼 곳부터 잘린다.
 * -> 강원은 10km 534건 / 30km 880건이라 잘리지 않는다.
 *
 * 참고로 서울 30km는 후보 4,181건 중 가까운 1,000건만 와서 실질 반경이
 * 6.4km가 된다. 수도권을 다루지 않기로 해 그대로 둔 선택이다.
 */
@Slf4j
@Component
public class RealtimePlaceFinder {

    /**한 번에 받아올 후보 수(1,000개)*/
    private static final int MAX_CANDIDATES = 1_000;

    /** 숙박의 관광타입 코드. `cat2`가 비어 있는 항목을 잡기 위한 두 번째 잣대다. */
    private static final String LODGING_CONTENT_TYPE_ID = "32";

    private final TourApiClient tourApiClient;

    /**
     * 난수원. 'RandomGenerator`를 직접 들고 있지 않고 공급자로 받는다.
     *
     * `ThreadLocalRandom.current()`는 "지금 이 스레드의 난수"를 주는 메서드라,
     * 필드에 한 번 담아 두면 다른 스레드가 그 인스턴스를 함께 쓰게 되어 의미가 깨진다.
     * 부를 때마다 새로 얻어야 한다. 테스트가 난수를 고정하는 통로이기도 하다.
     */
    private final Supplier<RandomGenerator> randomSource;

    @Autowired
    public RealtimePlaceFinder(TourApiClient tourApiClient) {
        this(tourApiClient, ThreadLocalRandom::current);
    }

    /** 테스트에서 난수를 고정하기 위한 생성자. */
    RealtimePlaceFinder(TourApiClient tourApiClient, Supplier<RandomGenerator> randomSource) {
        this.tourApiClient = tourApiClient;
        this.randomSource = randomSource;
    }

    /**
     * 반경 안에서 장소 하나를 뽑는다.
     *
     * 비어 있을 수 있다. 후보가 하나도 없다는 뜻이다. 여기서 예외를 던지지 않는 이유는,
     * 그렇게 하면 이 클래스가 "후보 없음은 404다"라는 HTTP 사정까지 알게 되기 때문이다.
     * 사실만 전하고 판단은 부르는 쪽(6-13)에 맡긴다.
     *
     * @param service       국문 관광정보 또는 무장애. 무장애를 고르면 등록된 장소만 후보가 된다
     * @param latitude      기준 위도 (숙소 좌표)
     * @param longitude     기준 경도
     * @param radiusKm      반경(km). 우리 도메인은 km로 말하고 API는 m로 받으므로 여기서 바꾼다
     * @param contentTypeId 관광 타입 코드. null이면 종류를 가리지 않는다 (결정 13의 기본값)
     */
    public Optional<TourApiPlaceItem> drawOne(
            TourApiService service, double latitude, double longitude,
            int radiusKm, String contentTypeId) {

        List<TourApiPlaceItem> received = tourApiClient.fetchLocationBasedList(
                service, latitude, longitude, radiusKm * 1000, contentTypeId, 1, MAX_CANDIDATES)
                .items();

        // 좌표가 없으면 places에 담을 수 없어(NOT NULL) 뽑아 놓고도 쓰지 못한다.
        // 뽑기 전에 걸러야 "뽑았는데 저장이 실패하는" 상황이 생기지 않는다.
        // 숙박은 애초에 여행지가 아니다(결정 18).
        List<TourApiPlaceItem> candidates = received.stream()
                .filter(item -> item.latitude() != null && item.longitude() != null)
                .filter(item -> !isLodging(item))
                .toList();

        if (candidates.size() < received.size()) {
            log.debug("좌표 없음·숙박으로 후보 {}건을 제외했습니다", received.size() - candidates.size());
        }

        if (candidates.isEmpty()) {
            log.debug("반경 {}km 안에 후보가 없습니다 — 좌표({}, {}), 종류 {}",
                    radiusKm, latitude, longitude, contentTypeId);
            return Optional.empty();
        }

        // 빈 목록을 먼저 걸러야 한다. nextInt(0)은 IllegalArgumentException이다.
        TourApiPlaceItem picked = candidates.get(randomSource.get().nextInt(candidates.size()));

        if (candidates.size() >= MAX_CANDIDATES) {
            // 잘렸다는 뜻이다. 반경을 넓혀도 결과가 그대로인 "이상한" 상황의 원인이 여기다.
            log.info("후보가 상한({})에 걸렸습니다 — 먼 곳은 추첨에서 빠집니다. 좌표({}, {}), 반경 {}km",
                    MAX_CANDIDATES, latitude, longitude, radiusKm);
        }
        return Optional.of(picked);
    }

    /**
     * 숙박 시설인지. (roadmap 6-16, 결정 18)
     *
     * 숙소는 온보딩에서 사용자에게 따로 입력받는다. 룰렛이 "오늘의 여행지: OO모텔"을 내놓을 이유가 없다.
     * 결정 13(종류를 안 보내면 전 종류 포괄)의 부작용으로 섞여 들어온 것이고,
     * 실측(2026-08-15) 강릉 30km 후보 880건 중 **190건(22%)**이 숙박이었다.
     *
     * **두 가지로 본다.** `cat2`가 `B0201`이거나, 관광타입이 `32`(숙박)이거나.
     * `cat2`는 실측에서 빈 값이 0건이었지만 비면 조용히 통과해 버리고,
     * 반대로 관광타입만 보면 나중에 숙박 관련 `cat2`가 늘었을 때 놓친다. 둘 중 하나만 맞아도 뺀다.
     *
     * ⚠️ **`contentTypeId=32`로 요청하면 후보가 전부 걸러져 `NO_PLACE_FOUND`(404)가 된다.**
     * "숙소는 룰렛에서 뽑지 않는다"를 예외 없이 적용하기로 한 결과다(사용자 결정 2026-08-17).
     */
    private static boolean isLodging(TourApiPlaceItem item) {
        String cat2 = item.cat2();
        if (cat2 != null && TravelCategory.LODGING.getCat2Code().equalsIgnoreCase(cat2.trim())) {
            return true;
        }
        String contentTypeId = item.contentTypeId();
        return contentTypeId != null && LODGING_CONTENT_TYPE_ID.equals(contentTypeId.trim());
    }
}
