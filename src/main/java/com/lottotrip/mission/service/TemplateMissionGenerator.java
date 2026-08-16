package com.lottotrip.mission.service;

import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 문구 틀에 장소 이름을 끼워 미션을 만드는 임시 구현. (roadmap 6-5)
 *
 * ⚠️ 이것은 자리를 채우는 구현이다. 생성 방식이 보류라 확정된 것이 아니며,
 * 회의 결과가 나오면 {@link MissionGenerator} 구현체를 갈아 끼워 대체한다.
 *
 * GPS 인증과 어긋나지 않게 "가서 하면 되는 것"만 담았다.
 */
@Component
public class TemplateMissionGenerator implements MissionGenerator {

    /**
     * 생성 미션의 보상 점수.
     * ⚠️ 잠정값(미확정 항목 "6-5 ③"). `reward_point`가 NOT NULL이라
     * 값이 없으면 저장 자체가 안 되어 우선 정함.
     */
    private static final int DEFAULT_REWARD_POINT = 100;

    /** `%s` 자리에 장소 이름이 들어간다. */
    private static final List<String> TITLE_TEMPLATES = List.of(
            "%s에 도착해 인증하기",
            "%s에서 사진 한 장 남기기",
            "%s 둘러보고 기억에 남은 곳 고르기",
            "%s에서 잠시 앉아 쉬어 가기");

    private static final String GUIDE = "현장에 도착하면 완료할 수 있는 미션입니다.";

    /**
     * 문구 틀 하나당 미션 하나를 만든다. **틀보다 많이 요청하면 있는 만큼만 준다.**
     *
     * 억지로 채우려면 같은 문구에 번호를 덧붙이는 수밖에 없는데, 그러면
     * `"경포대에 도착해 인증하기 (2)"` 같은 **사람이 쓰지 않을 문구가 사용자에게 그대로 나간다.**
     * 폴백 생성기가 만드는 제목은 화면에 보이는 값이므로 개수보다 문구가 우선이다.
     *
     * **적게 줘도 되는 근거:** 부르는 쪽인 {@link MissionMatcher}가 이미
     * "요청보다 적게 받는 것"을 허용한다(로그만 남기고 진행). 미션은 곁들이는 정보라
     * 개수가 모자라다고 슬롯을 실패시킬 이유가 없기 때문이다.
     *
     * 참고로 현재 요청되는 최대치는 3개(`REQUIRED_MISSION_COUNT`)라 틀 4개로 늘 충분하다.
     * 이 상한을 5 이상으로 올리면 **여기서 조용히 모자라게 준다** — 그때는 틀을 늘려야 한다.
     */
    @Override
    public List<Mission> generate(Place place, int count) {
        if (count <= 0) {
            return List.of();
        }

        int size = Math.min(count, TITLE_TEMPLATES.size());
        List<Mission> missions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String title = TITLE_TEMPLATES.get(i).formatted(place.getName());
            missions.add(Mission.create(place, title, GUIDE, null, DEFAULT_REWARD_POINT));
        }
        return missions;
    }
}
