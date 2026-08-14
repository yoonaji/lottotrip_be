package com.lottotrip.slot.service;

import com.lottotrip.place.dto.PlaceCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * 후보 중 한 곳을 뽑는다. (roadmap 6-4)
 *
 * <p><b>가중치를 다룰 수 있게 만들어 두고, 지금은 균등 정책을 끼운다.</b>
 * 확률 기준이 정해지면 {@link PlaceWeightPolicy} 구현체만 갈아 끼우면 되고 이 클래스는 그대로다.
 *
 * <h2>뽑는 방법</h2>
 * 가중치를 길이로 삼아 자를 하나 만든다고 생각하면 쉽다. 후보 A·B·C의 가중치가 1·1·2라면
 * 길이 4짜리 자에서 A는 [0,1), B는 [1,2), C는 [2,4) 구간을 차지한다.
 * 0~4 사이 아무 데나 찍어서 그 자리가 누구 구간인지 보면 된다. 구간이 넓을수록 자주 걸린다.
 */
@Component
public class PlaceDrawer {

    private final PlaceWeightPolicy weightPolicy;

    /**
     * 난수원. <b>{@code RandomGenerator}를 직접 들고 있지 않고 공급자로 받는다.</b>
     *
     * <p>기본값인 {@code ThreadLocalRandom}은 <b>스레드마다 다른 인스턴스를 써야</b> 한다.
     * {@code current()}의 결과를 필드에 담아 두면 그 스레드의 것이 다른 요청에서도 쓰여
     * 동시 요청이 몰릴 때 난수가 엉킨다. 부를 때마다 {@code current()}를 거치게 한다.
     *
     * <p>테스트가 난수를 고정할 수 있게 하는 통로이기도 하다. 난수를 안에서 만들면
     * 실행할 때마다 결과가 달라 "이 난수면 이 후보"를 검증할 수 없다.
     */
    private final Supplier<RandomGenerator> randomSource;

    /**
     * 스프링이 쓰는 생성자.
     *
     * <p>{@code @Autowired}를 붙인 이유: 생성자가 둘 이상이면 스프링은 어느 것을 쓸지 알 수 없어
     * 기본 생성자를 찾다가 실패한다. 하나만 있을 때는 자동으로 골라 주므로 생략할 수 있지만,
     * 아래 테스트용 생성자가 함께 있으므로 <b>어느 쪽인지 표시해 줘야 한다.</b>
     */
    @Autowired
    public PlaceDrawer(PlaceWeightPolicy weightPolicy) {
        this(weightPolicy, ThreadLocalRandom::current);
    }

    /** 테스트에서 난수를 고정하기 위한 생성자. */
    PlaceDrawer(PlaceWeightPolicy weightPolicy, Supplier<RandomGenerator> randomSource) {
        this.weightPolicy = weightPolicy;
        this.randomSource = randomSource;
    }

    /**
     * 후보 중 하나를 뽑는다.
     *
     * @param candidates 반경 안에서 찾은 후보들. <b>비어 있으면 안 된다</b>
     * @throws IllegalArgumentException 후보가 비어 있을 때. 후보가 없는 것은 정상 상황이지만
     *                                  그 판단({@code NO_PLACE_FOUND})은 부르는 쪽이 이미 했어야 한다.
     *                                  여기까지 왔다면 호출 순서가 잘못된 것이라 개발 중에 터져야 한다
     */
    public PlaceCandidate draw(List<PlaceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("추첨할 후보가 없습니다. 반경 조회 결과를 먼저 확인해야 합니다.");
        }

        double[] weights = new double[candidates.size()];
        double total = 0;
        for (int i = 0; i < candidates.size(); i++) {
            // 음수는 확률로 해석할 수 없다. 정책이 잘못 주더라도 "안 뽑힘"으로 다룬다.
            weights[i] = Math.max(0, weightPolicy.weightOf(candidates.get(i)));
            total += weights[i];
        }

        // 정책이 모두 0을 주면 나눌 수가 없다. 여기서 예외를 던지면 후보가 멀쩡히 있는데도
        // 슬롯이 실패한다. 뽑을 수 있는 후보가 있으면 무엇이든 돌려주는 편이 낫다.
        if (total <= 0) {
            return candidates.get(randomSource.get().nextInt(candidates.size()));
        }

        double target = randomSource.get().nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (target < cumulative) {
                return candidates.get(i);
            }
        }

        // 여기 오는 것은 부동소수점 오차로 누적합이 총합에 아주 살짝 못 미친 경우뿐이다.
        // 목록 밖을 가리키면 슬롯이 통째로 500이 되므로 마지막 후보로 받는다.
        return candidates.get(candidates.size() - 1);
    }
}
