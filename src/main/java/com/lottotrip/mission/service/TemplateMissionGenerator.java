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

    /**문구 틀 하나당 미션 하나를 만든다. 틀보다 많이 요청하면 있는 만큼만 준다.*/
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
