package com.lottotrip.slot.service;

import com.lottotrip.place.dto.PlaceCandidate;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 후보 중 한 곳을 뽑는 추첨 검증. (roadmap 6-4)
 *
 * <p>지금은 <b>균등 랜덤</b>이다(2026-08-14 사용자 결정). 가중치를 정할 근거 데이터가 없어
 * — {@code budget_tier}·{@code public_transport_weight}는 결정 9로 쓰지 않는다 —
 * 근거 없는 숫자를 박는 대신 {@link PlaceWeightPolicy}로 <b>끼워 넣을 자리만</b> 만들어 둔다.
 *
 * <p>난수를 쓰는 코드는 그냥 두면 <b>돌릴 때마다 결과가 달라 검증할 수 없다.</b>
 * 그래서 난수원을 밖에서 주입받게 하고, 여기서는 값을 고정해 "이 난수면 이 후보"를 못 박는다.
 */
class PlaceDrawerTest {

    /** 난수를 정해진 순서대로 돌려주는 가짜. "이 값이면 누가 뽑히나"를 정확히 못 박는다. */
    private static RandomGenerator fixedRandom(double... values) {
        return new RandomGenerator() {
            private int index;

            @Override
            public double nextDouble() {
                return values[index++ % values.length];
            }

            @Override
            public long nextLong() {
                throw new UnsupportedOperationException("이 테스트는 nextDouble만 쓴다");
            }
        };
    }

    private static PlaceCandidate candidate(String name, double distanceKm) {
        return new PlaceCandidate(Place.builder()
                .contentId("c-" + name)
                .contentTypeId("12")
                .name(name)
                .category(TravelCategory.NATURE)
                .latitude(37.75)
                .longitude(128.87)
                .build(), distanceKm);
    }

    private static PlaceDrawer uniformDrawer(RandomGenerator random) {
        return new PlaceDrawer(new UniformWeightPolicy(), () -> random);
    }

    @Test
    @DisplayName("후보가 하나뿐이면 그것을 뽑는다")
    void picksTheOnlyCandidate() {
        List<PlaceCandidate> candidates = List.of(candidate("유일", 1.0));

        PlaceCandidate picked = uniformDrawer(fixedRandom(0.0)).draw(candidates);

        assertThat(picked.place().getName()).isEqualTo("유일");
    }

    @Test
    @DisplayName("난수 위치에 따라 해당 구간의 후보가 뽑힌다")
    void picksByRandomPosition() {
        // 후보 3개가 균등하면 구간은 [0, 1/3) [1/3, 2/3) [2/3, 1)로 나뉜다.
        List<PlaceCandidate> candidates =
                List.of(candidate("A", 1.0), candidate("B", 2.0), candidate("C", 3.0));

        assertThat(uniformDrawer(fixedRandom(0.0)).draw(candidates).place().getName()).isEqualTo("A");
        assertThat(uniformDrawer(fixedRandom(0.5)).draw(candidates).place().getName()).isEqualTo("B");
        assertThat(uniformDrawer(fixedRandom(0.99)).draw(candidates).place().getName()).isEqualTo("C");
    }

    @Test
    @DisplayName("난수가 1에 아주 가까워도 목록 밖으로 나가지 않는다")
    void neverIndexesPastTheEnd() {
        // 누적 합과 총합을 부동소수점으로 비교하므로, 마지막 구간에서 아슬아슬하게
        // 어긋나면 인덱스가 목록을 벗어난다. 슬롯이 통째로 500이 되는 종류의 실패다.
        List<PlaceCandidate> candidates =
                List.of(candidate("A", 1.0), candidate("B", 2.0), candidate("C", 3.0));

        PlaceCandidate picked = uniformDrawer(fixedRandom(0.9999999999)).draw(candidates);

        assertThat(picked).isNotNull();
        assertThat(picked.place().getName()).isEqualTo("C");
    }

    @Test
    @DisplayName("균등 정책이면 오래 돌렸을 때 고르게 나온다")
    void spreadsEvenlyOverManyDraws() {
        // 구간 계산이 한쪽으로 치우쳐도 단발 테스트는 통과할 수 있다. 실제로 고르게
        // 나오는지는 여러 번 돌려 봐야 드러난다. 시드를 고정해 매번 같은 결과를 얻는다.
        List<PlaceCandidate> candidates =
                List.of(candidate("A", 1.0), candidate("B", 2.0), candidate("C", 3.0));
        PlaceDrawer drawer = uniformDrawer(new Random(42));

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 9_000; i++) {
            String name = drawer.draw(candidates).place().getName();
            counts.merge(name, 1, Integer::sum);
        }

        assertThat(counts).hasSize(3);
        assertThat(counts.values()).allSatisfy(count ->
                assertThat(count).isBetween(2_700, 3_300));   // 3,000 ± 10%
    }

    @Test
    @DisplayName("가중치가 크면 더 자주 뽑힌다")
    void favoursHeavierCandidates() {
        // 균등이 아닌 정책을 끼웠을 때 실제로 확률이 갈리는지 확인한다.
        // 이게 되지 않으면 확장 지점을 만들어 둔 의미가 없다.
        List<PlaceCandidate> candidates = List.of(candidate("가벼움", 1.0), candidate("무거움", 2.0));
        PlaceWeightPolicy heavierWhenFar =
                candidate -> candidate.distanceKm() >= 2.0 ? 9.0 : 1.0;
        // ⚠️ 인스턴스를 하나 만들어 계속 쓴다. () -> new Random(7)로 쓰면 부를 때마다
        //    같은 시드의 새 난수기가 생겨 항상 같은 첫 값이 나온다.
        Random random = new Random(7);
        PlaceDrawer drawer = new PlaceDrawer(heavierWhenFar, () -> random);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            counts.merge(drawer.draw(candidates).place().getName(), 1, Integer::sum);
        }

        // 1 : 9 이므로 대략 1,000 : 9,000
        assertThat(counts.get("무거움")).isBetween(8_500, 9_500);
        assertThat(counts.get("가벼움")).isBetween(500, 1_500);
    }

    @Test
    @DisplayName("가중치가 0인 후보는 뽑히지 않는다")
    void neverPicksZeroWeight() {
        List<PlaceCandidate> candidates = List.of(candidate("제외", 1.0), candidate("포함", 2.0));
        PlaceWeightPolicy excludeNear =
                candidate -> candidate.distanceKm() >= 2.0 ? 1.0 : 0.0;
        Random random = new Random(1);
        PlaceDrawer drawer = new PlaceDrawer(excludeNear, () -> random);

        for (int i = 0; i < 500; i++) {
            assertThat(drawer.draw(candidates).place().getName()).isEqualTo("포함");
        }
    }

    @Test
    @DisplayName("가중치가 전부 0이면 균등하게 뽑는다 — 아무도 못 뽑는 상황을 만들지 않는다")
    void fallsBackToUniformWhenAllWeightsAreZero() {
        // 정책이 잘못 짜여 모두 0을 주면 총합이 0이 되어 나눌 수가 없다.
        // 여기서 예외를 던지면 후보가 멀쩡히 있는데도 슬롯이 실패한다.
        // 뽑을 수 있는 후보가 있으면 무엇이든 돌려주는 편이 낫다.
        List<PlaceCandidate> candidates = List.of(candidate("A", 1.0), candidate("B", 2.0));
        Random random = new Random(3);
        PlaceDrawer drawer = new PlaceDrawer(candidate -> 0.0, () -> random);

        List<String> picked = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            picked.add(drawer.draw(candidates).place().getName());
        }

        assertThat(picked).contains("A", "B");
    }

    @Test
    @DisplayName("빈 목록으로 부르면 프로그래밍 오류로 본다")
    void rejectsEmptyCandidates() {
        // 후보가 없는 것은 정상 상황이지만(NO_PLACE_FOUND), 그 판단은 부르는 쪽(6-6)이
        // 이미 했어야 한다. 여기까지 빈 목록이 왔다면 순서가 잘못된 것이므로
        // 사용자에게 보여 줄 에러가 아니라 개발 중에 터져야 하는 신호다.
        PlaceDrawer drawer = uniformDrawer(new Random());

        assertThatThrownBy(() -> drawer.draw(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("균등 정책은 모든 후보에 같은 값을 준다")
    void uniformPolicyGivesEqualWeight() {
        PlaceWeightPolicy policy = new UniformWeightPolicy();

        assertThat(policy.weightOf(candidate("가까움", 0.1)))
                .isEqualTo(policy.weightOf(candidate("멀리", 29.9)));
    }
}
