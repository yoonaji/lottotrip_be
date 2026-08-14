package com.lottotrip.mission.service;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;

import java.util.List;

/**
 * 장소에 붙일 미션을 만든다. (roadmap 6-5)
 *
 * <p><b>⛔ 생성 방식은 아직 정해지지 않았다</b>(2026-08-13 회의에서 보류, 다음 회의로 넘김).
 * LLM으로 문구를 만들지, 키워드·템플릿으로 조합할지가 갈린다. 그래서 <b>인터페이스만 먼저 두고</b>
 * 매칭·선택 로직({@link MissionMatcher})을 완성해 둔다. 회의 결과가 나오면
 * <b>구현체 하나만 갈아 끼우면 된다</b> — 4-3의 {@code SocialTokenVerifier}와 같은 구조다.
 *
 * <p>⚠️ <b>LLM으로 정해지면 호출 위치를 다시 봐야 한다.</b> 지금 구조는 draw 도중에 생성하므로,
 * 생성이 2~5초 걸리면 사용자가 그만큼 기다린다. 그때는 적재 시점(5-9)에 미리 만들어 두는 편을
 * 함께 검토하기로 했다. 이 인터페이스는 어느 쪽이든 그대로 쓸 수 있다.
 */
@FunctionalInterface
public interface MissionGenerator {

    /**
     * 이 장소에 쓸 미션을 {@code count}개 만든다. <b>저장은 하지 않는다</b> — 부르는 쪽의 몫이다.
     *
     * <p><b>요청한 개수를 다 못 채워도 된다.</b> 생성이 일부만 성공하거나 아예 실패할 수 있는데,
     * 그때 예외를 던지면 곁들이는 정보인 미션 때문에 슬롯 전체가 실패한다.
     * 만들 수 있는 만큼만 돌려주면 {@link MissionMatcher}가 있는 것 중에서 고른다.
     *
     * @param place 미션이 붙을 장소
     * @param count 필요한 개수. 0 이하면 빈 목록을 돌려준다
     * @return 만들어진 미션들. 저장되지 않은 상태이며, 절대 {@code null}이 아니다
     */
    List<Mission> generate(Place place, int count);
}
