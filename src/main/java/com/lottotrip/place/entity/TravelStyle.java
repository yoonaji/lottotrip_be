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
 * 온보딩에서 고르는 여행 스타일 6종을 {@link TravelCategory}(TourAPI `cat2`) 묶음으로 옮긴 표.
 * (roadmap 6-16, 결정 19)
 *
 * 6-15에서 우리 분류 8종을 버리고 `cat2`로 옮긴 이유가 이 표다 — 예전 분류로는
 * 체험·활동·탐험이 전부 `LEISURE` 하나로 뭉쳐 서로 다른 스타일이 같은 후보를 줬다.
 *
 * ⚠️ 매핑 값은 잠정이다. 회의에서 확정되지 않아 강릉 30km 실측 분포를 보고 임의로 정했다
 * (사용자 결정 2026-08-17). 값이 정해지면 이 표만 고치면 된다.
 *
 * ⚠️ 아직 추첨에 쓰이지 않는다. `POST /slot/draw`는 스타일을 받지 않는다 — 잠정값 때문에
 * API 명세를 흔들지 않기로 했다. 붙이는 자리는 `RealtimePlaceFinder`의 후보 거르기다.
 *
 * 🔴 붙일 때 정할 것
 * 1. 휴식형(A0202)이 강릉 30km에 10건뿐이라 `walk`(10km)면 0건이 될 수 있다.
 *    필터로 쓰려면 "후보 없으면 전체 폴백"이 필요하다
 * 2. 쇼핑·축제·산업관광지·추천코스는 어느 스타일에도 없다. 버릴지 넣을지 미정
 *    (숙박은 결정 18로 후보에서 아예 뺐다)
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
     * 🔴 실측 10건뿐이다. 위 클래스 주석의 쟁점 1 참조.
     */
    REST("휴식형", TravelCategory.RELAXATION);

    /**
     * 분류로 스타일을 되찾기 위한 역방향 표.
     * 조회가 상수 시간이고, "한 분류가 두 스타일에 들어가면 안 된다"를 여기서 잡는다.
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
     * 이 분류가 어느 스타일에 속하는지. 어느 스타일에도 없는 분류가 실제로 있어 비어 있을 수 있다.
     * 예외가 아니라 빈 값인 이유는 그런 장소를 만나도 추첨이 멈춰선 안 되기 때문이다.
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
     * enum 상수의 생성자는 static 필드보다 먼저 돈다. 생성자 안에서 표에 넣으면 아직 만들어지지
     * 않은 `BY_CATEGORY`를 건드리게 되므로, 상수가 다 만들어진 뒤 `values()`로 훑는다.
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
