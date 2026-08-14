package com.lottotrip.mission.service;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 문구 틀에 장소 이름을 끼워 미션을 만드는 <b>임시</b> 구현. (roadmap 6-5)
 *
 * <p>⚠️ <b>이것은 자리를 채우는 구현이다.</b> 생성 방식이 보류라 확정된 것이 아니며,
 * 회의 결과가 나오면 {@link MissionGenerator} 구현체를 갈아 끼워 대체한다.
 * 그럼에도 최소 구현을 두는 이유는, 아무것도 만들지 않으면 <b>미션이 없는 장소가 뽑혔을 때
 * 응답이 비게 되어</b> 회의에서 "3개를 채운다"고 정한 취지가 무너지기 때문이다.
 *
 * <p><b>GPS 인증과 어긋나지 않게 "가서 하면 되는 것"만 담았다.</b> 8-1의 인증은 결국
 * "그 장소에 있었는가"만 판정하므로(미확정 항목 참조), 문구가 "일몰 사진"처럼 구체적이면
 * 실제 판정과 어긋난다. 도착하면 달성되는 종류로 두면 그 간극이 생기지 않는다.
 */
@Component
public class TemplateMissionGenerator implements MissionGenerator {

    /**
     * 생성 미션의 보상 점수.
     *
     * <p>⚠️ <b>잠정값이다</b>(미확정 항목 "6-5 ③"). {@code reward_point}가 NOT NULL이라
     * 값이 없으면 저장 자체가 안 되어 우선 정했다. 기준이 정해지면 이 상수만 고치면 된다.
     */
    private static final int DEFAULT_REWARD_POINT = 100;

    /** {@code %s} 자리에 장소 이름이 들어간다. */
    private static final List<String> TITLE_TEMPLATES = List.of(
            "%s에 도착해 인증하기",
            "%s에서 사진 한 장 남기기",
            "%s 둘러보고 기억에 남은 곳 고르기",
            "%s에서 잠시 앉아 쉬어 가기");

    private static final String GUIDE = "현장에 도착하면 완료할 수 있는 미션입니다.";

    @Override
    public List<Mission> generate(Place place, int count) {
        if (count <= 0) {
            return List.of();
        }

        List<Mission> missions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            missions.add(Mission.create(place, titleFor(place, i), GUIDE, null, DEFAULT_REWARD_POINT));
        }
        return missions;
    }

    /**
     * {@code index}번째 미션의 제목.
     *
     * <p>요청이 템플릿 개수를 넘으면 처음으로 돌아가는데, 그대로 두면 제목이 그대로 겹친다.
     * 같은 제목이 여러 개면 사용자에게 같은 미션이 반복해 보이고, 나중에
     * {@code (place_id, title)} UNIQUE를 걸기로 하면 저장 자체가 실패한다.
     * 그래서 한 바퀴를 넘어가면 번호를 덧붙여 구분한다.
     */
    private String titleFor(Place place, int index) {
        String title = TITLE_TEMPLATES.get(index % TITLE_TEMPLATES.size()).formatted(place.getName());
        int round = index / TITLE_TEMPLATES.size();
        return round == 0 ? title : title + " (" + (round + 1) + ")";
    }
}
