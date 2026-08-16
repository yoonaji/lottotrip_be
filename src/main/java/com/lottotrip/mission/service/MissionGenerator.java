package com.lottotrip.mission.service;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;

import java.util.List;

/**
 * 장소에 붙일 미션을 만든다. (roadmap 6-5)
 */
@FunctionalInterface
public interface MissionGenerator {

    /**
     * 이 장소에 쓸 미션을 `count`개 만든다. 저장은 하지 않는다 — 부르는 쪽의 몫이다.
     * 만들 수 있는 만큼만 돌려주면 {@link MissionMatcher}가 있는 것 중에서 고른다.
     *
     * @param place 미션이 붙을 장소
     * @param count 필요한 개수. 0 이하면 빈 목록을 돌려준다
     * @return 만들어진 미션들. 저장되지 않은 상태이며, 절대 `null`이 아니다
     */
    List<Mission> generate(Place place, int count);
}
