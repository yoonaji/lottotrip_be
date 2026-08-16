package com.lottotrip.mission.service;

import com.lottotrip.mission.claude.ClaudeMissionClient;
import com.lottotrip.mission.claude.MissionCopy;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Claude 미션 생성기 검증. (roadmap 8-5, 결정 17)
 *
 * **실제 Claude API를 부르지 않는다.** 부르면 테스트가 네트워크·키·요금·응답 내용에
 * 통째로 의존해서, 실패했을 때 "우리 코드가 틀렸는지" "밖이 문제인지"를 구분할 수 없다.
 * 그래서 API를 부르는 부분({@link ClaudeMissionClient})을 **가짜(mock)**로 바꿔 두고,
 * 우리가 책임지는 두 가지만 본다.
 *   - **응답을 미션으로 옮기는가** — 문구 목록이 저장 가능한 `Mission`이 되는가
 *   - **실패했을 때 템플릿으로 내려가는가** — 키 미설정·예외·빈 응답
 *
 * 실물 호출 1회 확인은 `ClaudeMissionClientManualTest`에 `@Tag("manual")`로
 * 따로 떼어 두었다. 평소 빌드에서는 실행되지 않는다.
 */
class ClaudeMissionGeneratorTest {

    private static final Place PLACE = Place.builder()
            .contentId("c-1")
            .contentTypeId("12")
            .name("사천진해변")
            .category(TravelCategory.NATURE_ATTRACTION)
            .latitude(37.8021)
            .longitude(128.8954)
            .build();

    private ClaudeMissionClient client;
    private ClaudeMissionGenerator generator;

    @BeforeEach
    void setUp() {
        client = mock(ClaudeMissionClient.class);
        given(client.enabled()).willReturn(true);
        generator = new ClaudeMissionGenerator(client, new TemplateMissionGenerator());
    }

    /** 템플릿 문구는 장소 이름이 들어가는 고정 틀이라, 이것이 아니면 Claude 결과다. */
    private boolean looksLikeTemplate(Mission mission) {
        return mission.getTitle().startsWith("사천진해변에 도착해 인증하기")
                || mission.getTitle().startsWith("사천진해변에서 사진 한 장 남기기")
                || mission.getTitle().startsWith("사천진해변 둘러보고")
                || mission.getTitle().startsWith("사천진해변에서 잠시 앉아");
    }

    // ---------- 정상 응답 ----------

    @Test
    @DisplayName("Claude가 준 문구로 미션을 만든다")
    void mapsCopiesToMissions() {
        given(client.write(PLACE, 2)).willReturn(List.of(
                new MissionCopy("사천진해변 백사장 끝까지 걸어보기", "모래를 따라 끝까지 가 보세요."),
                new MissionCopy("사천진해변 방파제 앞에서 인증하기", "빨간 등대가 보이는 자리입니다.")));

        List<Mission> missions = generator.generate(PLACE, 2);

        assertThat(missions).hasSize(2);
        assertThat(missions).extracting(Mission::getTitle)
                .containsExactly("사천진해변 백사장 끝까지 걸어보기", "사천진해변 방파제 앞에서 인증하기");
        assertThat(missions).extracting(Mission::getGuideDescription)
                .containsExactly("모래를 따라 끝까지 가 보세요.", "빨간 등대가 보이는 자리입니다.");
    }

    @Test
    @DisplayName("저장할 수 있는 형태로 만든다 — 장소와 reward_point가 채워진다")
    void producesPersistableMissions() {
        // reward_point는 NOT NULL이다. 비어 있으면 저장 시점에 터진다(템플릿 구현과 같은 계약).
        given(client.write(PLACE, 1)).willReturn(List.of(new MissionCopy("등대까지 걸어가기", "설명")));

        Mission mission = generator.generate(PLACE, 1).get(0);

        assertThat(mission.getPlace()).isSameAs(PLACE);
        assertThat(mission.getRewardPoint()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("요청보다 많이 오면 요청한 개수만 쓴다")
    void trimsExtraCopies() {
        // 모델은 개수를 지키지 않을 수 있다. 그대로 저장하면 장소마다 미션이 필요 이상으로 쌓인다.
        given(client.write(PLACE, 1)).willReturn(List.of(
                new MissionCopy("첫 번째", "설명"),
                new MissionCopy("두 번째", "설명")));

        assertThat(generator.generate(PLACE, 1)).hasSize(1);
    }

    @Test
    @DisplayName("제목이 비어 있는 항목은 버린다")
    void dropsBlankTitles() {
        // title은 NOT NULL이고 사용자에게 그대로 보인다. 빈 제목이 섞여 저장되면 빈 미션이 발급된다.
        given(client.write(PLACE, 2)).willReturn(List.of(
                new MissionCopy("등대까지 걸어가기", "설명"),
                new MissionCopy("  ", "설명")));

        assertThat(generator.generate(PLACE, 2))
                .singleElement()
                .satisfies(mission -> assertThat(mission.getTitle()).isEqualTo("등대까지 걸어가기"));
    }

    @Test
    @DisplayName("컬럼 길이(100자)를 넘는 제목은 버린다")
    void dropsOverlongTitles() {
        // missions.title은 VARCHAR(100)이다. 넘는 값을 그대로 저장하면 INSERT가 터져
        // 미션 때문에 draw 전체가 실패한다. 잘라 붙이면 문장이 중간에서 끊겨 더 이상하다.
        given(client.write(PLACE, 2)).willReturn(List.of(
                new MissionCopy("가".repeat(101), "설명"),
                new MissionCopy("등대까지 걸어가기", "설명")));

        assertThat(generator.generate(PLACE, 2))
                .singleElement()
                .satisfies(mission -> assertThat(mission.getTitle()).isEqualTo("등대까지 걸어가기"));
    }

    @Test
    @DisplayName("0개 이하를 요청하면 Claude를 부르지 않는다")
    void skipsCallForNonPositiveCount() {
        assertThat(generator.generate(PLACE, 0)).isEmpty();
        assertThat(generator.generate(PLACE, -1)).isEmpty();

        verify(client, never()).write(any(), anyInt());
    }

    // ---------- 폴백 (템플릿으로 내려가는 경우) ----------

    @Test
    @DisplayName("API 키가 없으면 템플릿으로 만든다 — 부르지도 않는다")
    void fallsBackWhenDisabled() {
        // 키는 개인 환경에 따라 없을 수 있다. 그때 미션이 통째로 사라지면 슬롯 응답이 빈다.
        given(client.enabled()).willReturn(false);

        List<Mission> missions = generator.generate(PLACE, 3);

        assertThat(missions).hasSize(3).allMatch(this::looksLikeTemplate);
        verify(client, never()).write(any(), anyInt());
    }

    @Test
    @DisplayName("호출이 실패하면 템플릿으로 내려간다")
    void fallsBackWhenCallFails() {
        // 장애·타임아웃·요금 한도. 어느 쪽이든 예외를 그대로 올리면 draw 전체가 실패한다.
        willThrow(new RuntimeException("timeout")).given(client).write(PLACE, 3);

        assertThat(generator.generate(PLACE, 3)).hasSize(3).allMatch(this::looksLikeTemplate);
    }

    @Test
    @DisplayName("쓸 만한 문구가 하나도 없으면 템플릿으로 내려간다")
    void fallsBackWhenNothingUsable() {
        given(client.write(PLACE, 3)).willReturn(List.of(new MissionCopy("", "설명")));

        assertThat(generator.generate(PLACE, 3)).hasSize(3).allMatch(this::looksLikeTemplate);
    }

    @Test
    @DisplayName("일부만 왔으면 그만큼만 돌려준다 — 템플릿으로 채우지 않는다")
    void keepsPartialResult() {
        // 부족한 만큼은 MissionMatcher가 다음 draw에서 다시 채운다(인터페이스 계약).
        // 여기서 템플릿을 섞으면 같은 장소에 성격이 다른 문구가 뒤섞인다.
        given(client.write(PLACE, 3)).willReturn(List.of(new MissionCopy("등대까지 걸어가기", "설명")));

        assertThat(generator.generate(PLACE, 3))
                .singleElement()
                .satisfies(mission -> assertThat(mission.getTitle()).isEqualTo("등대까지 걸어가기"));
    }
}
