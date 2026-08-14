package com.lottotrip.slot.service;

import com.lottotrip.place.dto.PlaceCandidate;
import org.springframework.stereotype.Component;

/**
 * 모든 후보를 같은 확률로 다루는 기본 정책. (roadmap 6-4, 2026-08-14 사용자 결정)
 *
 * <p>반경 안에 있다는 것 외에 후보를 차등할 근거가 아직 없다. 없는 근거로 확률을 흔들면
 * "왜 이 장소가 자주 나오냐"는 물음에 답할 수 없게 되므로, <b>기준이 정해질 때까지 균등을 쓴다.</b>
 *
 * <p>슬롯머신의 "랜덤 여행지" 컨셉과도 어긋나지 않는다 — 어디가 나올지 모르는 것이 원래 재미다.
 */
@Component
public class UniformWeightPolicy implements PlaceWeightPolicy {

    /** 값이 무엇이든 모두 같기만 하면 결과는 같다. 1이 읽기 쉬워 1로 둔다. */
    private static final double EQUAL_WEIGHT = 1.0;

    @Override
    public double weightOf(PlaceCandidate candidate) {
        return EQUAL_WEIGHT;
    }
}
