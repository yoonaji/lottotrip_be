package com.lottotrip.place.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행 스타일 6종 ↔ `cat2` 매핑 검증. (roadmap 6-16, 결정 19)
 *
 * ⚠️ 매핑 값은 잠정이다. 아래 테스트는 값이 맞다는 증명이 아니라 지금 무엇으로 정해 뒀는지의
 * 기록이라, 회의에서 값이 바뀌면 함께 고친다.
 *
 * 지키는 것은 값이 아니라 성질 두 가지 — 한 분류가 두 스타일에 겹치지 않을 것,
 * 어느 스타일에도 없는 분류가 드러날 것.
 */
class TravelStyleTest {

    // ---------- 매핑 (잠정값) ----------

    @Test
    @DisplayName("맛집형은 음식점이다")
    void mapsFood() {
        assertThat(TravelStyle.FOOD.getCategories())
                .containsExactly(TravelCategory.RESTAURANT);
    }

    @Test
    @DisplayName("활동형은 레포츠 5종 전부다 — A03 아래를 통째로 가져간다")
    void mapsActivity() {
        assertThat(TravelStyle.ACTIVITY.getCategories()).containsExactlyInAnyOrder(
                TravelCategory.SPORTS_INFO, TravelCategory.LAND_SPORTS,
                TravelCategory.WATER_SPORTS, TravelCategory.AIR_SPORTS,
                TravelCategory.MIXED_SPORTS);
    }

    @Test
    @DisplayName("감상형은 보는 곳들이다 — 역사·건축·문화시설·공연")
    void mapsSightseeing() {
        assertThat(TravelStyle.SIGHTSEEING.getCategories()).containsExactlyInAnyOrder(
                TravelCategory.HISTORY, TravelCategory.ARCHITECTURE,
                TravelCategory.CULTURE_FACILITY, TravelCategory.PERFORMANCE);
    }

    @Test
    @DisplayName("탐험형은 자연 계열 2종이다")
    void mapsExploration() {
        assertThat(TravelStyle.EXPLORATION.getCategories()).containsExactlyInAnyOrder(
                TravelCategory.NATURE_ATTRACTION, TravelCategory.NATURE_RESOURCE);
    }

    @Test
    @DisplayName("체험형·휴식형은 cat2에 이름 그대로 있다 — 이것 때문에 cat2 체계를 택했다")
    void mapsExperienceAndRest() {
        // 6-15에서 우리 분류 8종을 버리고 cat2로 옮긴 이유가 여기다.
        // 예전 분류로는 체험·활동·탐험이 전부 LEISURE 하나로 뭉쳤다.
        assertThat(TravelStyle.EXPERIENCE.getCategories())
                .containsExactly(TravelCategory.EXPERIENCE);
        assertThat(TravelStyle.REST.getCategories())
                .containsExactly(TravelCategory.RELAXATION);
    }

    // ---------- 성질 ----------

    @Test
    @DisplayName("스타일은 6종이다")
    void hasSixStyles() {
        assertThat(TravelStyle.values()).hasSize(6);
    }

    @Test
    @DisplayName("한 분류는 많아야 한 스타일에만 든다 — 겹치면 장소의 스타일이 하나로 안 정해진다")
    void categoriesDoNotOverlap() {
        Set<TravelCategory> seen = new HashSet<>();
        for (TravelStyle style : TravelStyle.values()) {
            for (TravelCategory category : style.getCategories()) {
                assertThat(seen.add(category))
                        .as("%s 가 두 스타일에 들어 있다", category)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("분류로 스타일을 되찾을 수 있다")
    void findsStyleByCategory() {
        assertThat(TravelStyle.of(TravelCategory.RESTAURANT)).contains(TravelStyle.FOOD);
        assertThat(TravelStyle.of(TravelCategory.WATER_SPORTS)).contains(TravelStyle.ACTIVITY);
    }

    @Test
    @DisplayName("어느 스타일에도 없는 분류는 빈 값이다 — 예외가 아니다")
    void returnsEmptyForUnmappedCategory() {
        // 🔴 쟁점 ② — 쇼핑·축제·산업관광지·추천코스는 어느 스타일에도 안 들어간다.
        // 버릴지 어딘가에 넣을지 미정이라, 지금은 "스타일 없음"으로 둔다.
        assertThat(TravelStyle.of(TravelCategory.SHOPPING)).isEmpty();
        assertThat(TravelStyle.of(TravelCategory.FESTIVAL)).isEmpty();
        assertThat(TravelStyle.of(TravelCategory.INDUSTRY)).isEmpty();
        assertThat(TravelStyle.of(TravelCategory.FAMILY_COURSE)).isEmpty();
        assertThat(TravelStyle.of(TravelCategory.UNKNOWN)).isEmpty();
    }

    @Test
    @DisplayName("숙박은 어느 스타일에도 없다 — 룰렛 후보에서 아예 뺐다 (결정 18)")
    void lodgingBelongsToNoStyle() {
        assertThat(TravelStyle.of(TravelCategory.LODGING)).isEmpty();
    }

    @Test
    @DisplayName("null을 물어도 빈 값이다 — 부르는 쪽이 null을 먼저 확인하지 않아도 된다")
    void handlesNullCategory() {
        assertThat(TravelStyle.of(null)).isEmpty();
    }

    @Test
    @DisplayName("includes와 of는 같은 것을 말한다")
    void includesAgreesWithOf() {
        for (TravelCategory category : TravelCategory.values()) {
            Optional<TravelStyle> found = TravelStyle.of(category);
            for (TravelStyle style : TravelStyle.values()) {
                assertThat(style.includes(category))
                        .as("%s.includes(%s)", style, category)
                        .isEqualTo(found.filter(style::equals).isPresent());
            }
        }
    }

    @Test
    @DisplayName("스타일 목록은 밖에서 못 바꾼다 — 매핑이 실행 중에 흔들리면 안 된다")
    void categoriesAreImmutable() {
        assertThat(TravelStyle.FOOD.getCategories().getClass().getSimpleName())
                .doesNotContain("HashSet"); // 수정 가능한 구현이 그대로 새어 나오면 안 된다
        assertThat(Arrays.stream(TravelStyle.values())
                .allMatch(style -> {
                    try {
                        style.getCategories().clear();
                        return false;
                    } catch (UnsupportedOperationException expected) {
                        return true;
                    }
                })).isTrue();
    }

    // ---------- 표시명 ----------

    @Test
    @DisplayName("모든 스타일에 사람이 읽을 이름이 있다")
    void everyStyleHasDisplayName() {
        assertThat(TravelStyle.values()).allSatisfy(style ->
                assertThat(style.getDisplayName()).as("%s", style).isNotBlank());
    }

    @Test
    @DisplayName("표시명은 회의에서 쓴 6종 이름 그대로다")
    void usesMeetingNames() {
        assertThat(TravelStyle.REST.getDisplayName()).isEqualTo("휴식형");
        assertThat(TravelStyle.FOOD.getDisplayName()).isEqualTo("맛집형");
        assertThat(TravelStyle.SIGHTSEEING.getDisplayName()).isEqualTo("감상형");
        assertThat(TravelStyle.EXPERIENCE.getDisplayName()).isEqualTo("체험형");
        assertThat(TravelStyle.ACTIVITY.getDisplayName()).isEqualTo("활동형");
        assertThat(TravelStyle.EXPLORATION.getDisplayName()).isEqualTo("탐험형");
    }
}
