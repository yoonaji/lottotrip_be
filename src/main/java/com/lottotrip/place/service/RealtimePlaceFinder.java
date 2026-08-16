package com.lottotrip.place.service;

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
 * **슬롯 추첨의 전부가 여기서 일어난다.** 결정 12로 배치 적재가 없어지면서, 사용자가 무엇을
 * 보게 되는지는 이 클래스가 어떤 후보를 받아 오고 그중 무엇을 고르는가로 정해진다.
 *
 * ## 왜 한 번만 부르는가
 * 후보 수를 먼저 알아보고 그다음 하나를 집어 오는 방법도 있지만(호출 2회), 실측해 보니
 * **응답 시간이 건수에 거의 영향받지 않는다** — 100건 0.37초 vs 880건 0.39초.
 * 시간의 대부분이 왕복 지연이라, **한 번에 다 받는 편이 두 번 묻는 것보다 오히려 빠르다.**
 * 게다가 일일 할당량을 절반만 쓴다.
 *
 * **페이지를 넘기지 않는다.** 2026-08-14에 페이지 순회가 끝나지 않아 할당량 1,000회를
 * 통째로 태운 적이 있다. 순회 자체를 하지 않으면 그 사고가 구조적으로 불가능해진다.
 *
 * ## ⚠️ 한계 — 후보 1,000건까지만 닿는다
 * `numOfRows`의 API 상한이 1,000이다. 그보다 많으면 `arrange=E`(거리순) 덕에
 * **먼 곳부터 잘린다.** 서울 30km는 후보 4,181건 중 가까운 1,000건만 와서 실질 반경이
 * 6.4km가 된다. **수도권을 다루지 않기로 해 그대로 둔 선택이다**(결정 15).
 * 강원은 10km 534건 / 30km 880건이라 잘리지 않는다.
 */
@Slf4j
@Component
public class RealtimePlaceFinder {

    /**
     * 한 번에 받아올 후보 수. **API 상한과 같은 값이다.**
     *
     * 더 작게 잡으면 반경 안에 있는데도 뽑힐 수 없는 장소가 생긴다.
     * 예컨대 100으로 두면 강릉 30km의 880곳 중 가까운 100곳에서만 뽑힌다.
     */
    private static final int MAX_CANDIDATES = 1_000;

    private final TourApiClient tourApiClient;

    /**
     * 난수원. **`RandomGenerator`를 직접 들고 있지 않고 공급자로 받는다.**
     *
     * `ThreadLocalRandom.current()`는 "지금 이 스레드의 난수"를 주는 메서드라,
     * 필드에 한 번 담아 두면 다른 스레드가 그 인스턴스를 함께 쓰게 되어 의미가 깨진다.
     * 부를 때마다 새로 얻어야 한다. 테스트가 난수를 고정하는 통로이기도 하다.
     */
    private final Supplier<RandomGenerator> randomSource;

    /**
     * 스프링이 쓰는 생성자.
     *
     * `@Autowired`를 붙인 이유: 생성자가 둘 이상이면 스프링은 어느 것을 쓸지 알 수 없어
     * 기본 생성자를 찾다가 **`No default constructor found`로 기동 자체가 실패한다.**
     * 하나만 있을 때는 자동으로 골라 주므로 생략할 수 있지만, 아래 테스트용 생성자가 함께 있으므로
     * 어느 쪽인지 표시해 줘야 한다. (`MissionMatcher`도 같은 이유로 붙어 있다)
     */
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
     * **비어 있을 수 있다.** 후보가 하나도 없다는 뜻이다. 여기서 예외를 던지지 않는 이유는,
     * 그렇게 하면 이 클래스가 **"후보 없음은 404다"라는 HTTP 사정까지 알게 되기** 때문이다.
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
        List<TourApiPlaceItem> candidates = received.stream()
                .filter(item -> item.latitude() != null && item.longitude() != null)
                .toList();

        if (candidates.size() < received.size()) {
            log.debug("좌표가 없는 후보 {}건을 제외했습니다", received.size() - candidates.size());
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
}
