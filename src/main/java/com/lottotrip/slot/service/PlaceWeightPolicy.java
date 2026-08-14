package com.lottotrip.slot.service;

import com.lottotrip.place.dto.PlaceCandidate;

/**
 * 후보 하나에 얼마만큼의 뽑힐 몫을 줄지 정한다. (roadmap 6-4)
 *
 * <p><b>지금은 균등이다</b>({@link UniformWeightPolicy}). 2026-08-14에 사용자와 확인한 결과
 * 가중치를 정할 근거 데이터가 없었다 — 원래 쓰려던 {@code budget_tier}·
 * {@code public_transport_weight}는 결정 9로 채우지 않기로 했고, "당첨/꽝" 라벨도
 * 저장할 곳(`saved_slots`)이나 산정 기준이 정해진 적이 없다.
 *
 * <p><b>그런데도 인터페이스를 두는 이유:</b> 근거 없는 숫자를 추첨 코드 안에 박아 두면
 * 나중에 그 숫자가 어디서 왔는지 아무도 모르게 된다. 기준이 정해지는 시점에
 * <b>구현체 하나만 갈아 끼우면 되도록</b> 자리를 비워 둔다. 추첨 알고리즘 자체는
 * 이미 가중치를 다룰 수 있게 되어 있으므로 {@link PlaceDrawer}는 고칠 필요가 없다.
 *
 * <p>기준이 정해지면 예를 들어 이런 구현이 올 수 있다.
 * <ul>
 *   <li>카테고리별 배율 — 해변·자연을 더 자주</li>
 *   <li>거리 기반 — 가까운 곳을 더 자주, 또는 먼 곳을 "대박"으로 드물게</li>
 *   <li>{@code places}에 새로 담은 가중치 컬럼</li>
 * </ul>
 */
@FunctionalInterface
public interface PlaceWeightPolicy {

    /**
     * 이 후보의 가중치. <b>0 이상이어야 한다.</b>
     *
     * <p>클수록 자주 뽑힌다. 0을 주면 그 후보는 뽑히지 않는다 — 목록에서 빼지 않고도
     * 제외할 수 있는 셈이다. 음수는 확률로 해석할 수 없어 {@link PlaceDrawer}가 0으로 본다.
     */
    double weightOf(PlaceCandidate candidate);
}
