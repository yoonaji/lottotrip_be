package com.lottotrip.place.entity;

import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;

/**
 * 온보딩에서 고르는 여행 스타일 6종. (roadmap 6-16, 결정 19)
 *
 * 회의에서 "휴식·맛집·감상·체험·활동·탐험 중에 고르게 하자"고 정해 둔 6종이고,
 * 이 클래스는 그 6종을 **{@link TravelCategory}(TourAPI `cat2`) 묶음으로 옮긴 표**다.
 * 6-15에서 우리 분류 8종을 버리고 `cat2` 체계로 옮긴 이유가 바로 이 표를 만들기 위해서였다 —
 * 예전 분류로는 체험·활동·탐험이 전부 `LEISURE` 하나로 뭉쳐 서로 다른 스타일이 같은 후보를 줬다.
 *
 * ## ⚠️ 이 매핑은 잠정값이다 (확정 아님)
 * 회의에서 확정되지 않았고, **강릉 30km 실측 분포(2026-08-15)를 보고 임의로 정했다**
 * (사용자 결정 2026-08-17 — "일단 임의로 매핑"). 회의에서 값이 정해지면 이 표만 고치면 된다.
 *
 * ## ⚠️ 아직 추첨에 쓰이지 않는다
 * `POST /slot/draw`는 스타일을 받지 않는다. 요청 필드를 늘리면 API 명세가 바뀌어
 * 프론트와 맞춰야 하는데, 매핑 값이 확정되기 전에 명세부터 흔들 이유가 없다고 보아
 * **표만 먼저 만들어 두기로 했다**(사용자 결정 2026-08-17).
 * 붙이는 자리는 `RealtimePlaceFinder`의 후보 거르기 한 곳이다.
 *
 * ## 🔴 붙일 때 함께 정해야 하는 것 두 가지
 * 1. **휴식형 후보가 너무 적다.** `RELAXATION`(A0202)은 강릉 30km에 10건뿐이라
 *    `walk`(10km)로 좁히면 0건이 될 수 있다. 그대로 필터로 쓰면 `NO_PLACE_FOUND`가 된다 —
 *    "후보 없으면 전체에서 뽑는" 폴백이 필요해 보인다.
 * 2. **어느 스타일에도 없는 분류가 있다.** 쇼핑(A0401)·축제(A0207)·산업관광지(A0204)·
 *    추천코스(C01\*)가 그렇다. 버릴지 어딘가에 넣을지 미정이다.
 *    숙박(B0201)은 이 문제가 아니다 — 결정 18로 **추첨 후보에서 아예 뺐다.**
 */
@Getter
public enum TravelStyle {

    /** 강릉 30km 기준 486건으로 가장 많다. 그만큼 다른 스타일보다 쉽게 채워진다. */
    FOOD("맛집형", TravelCategory.RESTAURANT),

    /** `A03`(레포츠) 아래를 통째로 가져간다. 실측 53건. */
    ACTIVITY("활동형",
            TravelCategory.SPORTS_INFO,
            TravelCategory.LAND_SPORTS,
            TravelCategory.WATER_SPORTS,
            TravelCategory.AIR_SPORTS,
            TravelCategory.MIXED_SPORTS),

    /** "보러 가는 곳"으로 묶었다. 실측 44건. */
    SIGHTSEEING("감상형",
            TravelCategory.HISTORY,
            TravelCategory.ARCHITECTURE,
            TravelCategory.CULTURE_FACILITY,
            TravelCategory.PERFORMANCE),

    /** 자연 계열 2종. 해수욕장이 `NATURE_ATTRACTION`에 묻혀 있어 바다도 여기로 온다. 실측 31건. */
    EXPLORATION("탐험형",
            TravelCategory.NATURE_ATTRACTION,
            TravelCategory.NATURE_RESOURCE),

    /** `cat2`에 "체험관광지"가 이름 그대로 있다. 실측 27건. */
    EXPERIENCE("체험형", TravelCategory.EXPERIENCE),

    /**
     * `cat2`의 "휴양관광지"가 그대로 대응한다.
     * 🔴 **실측 10건뿐이다.** 위 클래스 주석의 쟁점 1 참조.
     */
    REST("휴식형", TravelCategory.RELAXATION);

    /**
     * 분류로 스타일을 되찾기 위한 역방향 표.
     *
     * 매번 6종을 훑어도 느리지 않지만, 표를 한 번만 만들어 두면 조회가 상수 시간이고
     * **"한 분류가 두 스타일에 들어가면 안 된다"**는 성질이 여기서 드러난다.
     * (`Map.of`에 같은 키를 두 번 넣으면 클래스가 로딩되는 순간 터진다.)
     */
    private static final Map<TravelCategory, TravelStyle> BY_CATEGORY = buildIndex();

    private final String displayName;

    /** 이 스타일에 속하는 분류들. 밖에서 못 바꾼다. */
    private final Set<TravelCategory> categories;

    TravelStyle(String displayName, TravelCategory... categories) {
        this.displayName = displayName;
        // EnumSet은 enum 전용 집합이라 비트 하나로 원소 하나를 표현한다. 담을 값이 정해져 있을 때 가장 가볍다.
        this.categories = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(categories)));
    }

    /**
     * 이 분류가 어느 스타일에 속하는지.
     *
     * 비어 있을 수 있다. **어느 스타일에도 안 들어가는 분류가 실제로 있다**(쟁점 2).
     * 예외가 아니라 빈 값으로 주는 이유는, 그런 장소를 만나도 추첨이 멈춰선 안 되기 때문이다.
     *
     * @param category null이어도 된다
     */
    public static Optional<TravelStyle> of(TravelCategory category) {
        return Optional.ofNullable(BY_CATEGORY.get(category));
    }

    /** 이 스타일이 그 분류를 품고 있는지. */
    public boolean includes(TravelCategory category) {
        return categories.contains(category);
    }

    /**
     * 역방향 표를 만든다.
     *
     * enum 상수의 생성자는 static 필드보다 **먼저** 돈다. 그래서 생성자 안에서 표에 넣으려 하면
     * 아직 만들어지지 않은 `BY_CATEGORY`를 건드리게 된다. 상수가 모두 만들어진 뒤에
     * `values()`로 한 번에 훑는 이 방식이 그 순서 문제를 피한다.
     */
    private static Map<TravelCategory, TravelStyle> buildIndex() {
        Map<TravelCategory, TravelStyle> index = new HashMap<>();
        for (TravelStyle style : values()) {
            for (TravelCategory category : style.categories) {
                TravelStyle previous = index.put(category, style);
                if (previous != null) {
                    // 겹치면 "이 장소의 스타일"이 하나로 정해지지 않는다. 조용히 덮어쓰면 안 된다.
                    throw new IllegalStateException(
                            "%s 가 %s 와 %s 두 스타일에 들어 있다".formatted(category, previous, style));
                }
            }
        }
        return Collections.unmodifiableMap(index);
    }
}
