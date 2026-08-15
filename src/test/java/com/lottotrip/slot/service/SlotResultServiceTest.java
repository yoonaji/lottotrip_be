package com.lottotrip.slot.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.service.PlaceDetailService;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiProperties;
import com.lottotrip.slot.dto.SlotResultResponse;
import com.lottotrip.slot.entity.SavedSlot;
import com.lottotrip.slot.entity.TransportType;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 슬롯 결과 조회 검증. (roadmap 6-7, tour_api_erd.md 4-2 + 결정 10)
 *
 * <p><b>이 API가 "룰렛 세부사항 조회"를 겸한다.</b> 결정 10으로 추첨이 DB에서만 이뤄지므로,
 * <b>사용자 요청에 반응해 공공데이터 API를 부르는 지점이 여기 하나뿐이다.</b>
 * 공모전 규정(결정 7 — 오픈API 실시간 호출 필수)을 만족시키는 것도 이 호출이다.
 *
 * <p>그래서 <b>바깥이 실패해도 우리 정보는 나가야 한다</b>는 성질이 특히 중요하다.
 * 공공데이터포털이 멈췄다고 사용자가 방금 뽑은 장소를 못 보면 안 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SlotResultServiceTest extends PostgresContainerSupport {

    private static final String DETAIL_RESPONSE = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": {
                  "items": { "item": [ {
                    "contentid": "126508",
                    "overview": "동해안의 조용한 해변입니다.",
                    "homepage": "<a href=\\"https://gn.go.kr\\" target=\\"_blank\\">강릉시청</a>"
                  } ] },
                  "numOfRows": 1, "pageNo": 1, "totalCount": 1
                }
              }
            }
            """;

    @Autowired
    private SavedSlotRepository savedSlotRepository;
    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private MissionRepository missionRepository;

    private MockRestServiceServer mockServer;
    private SlotResultService slotResultService;
    private User user;
    private Place place;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder, new TourApiProperties(
                "https://apis.data.go.kr/B551011/KorService2", null, "test-key", null, null, 100));
        slotResultService = new SlotResultService(
                savedSlotRepository, missionRepository, new PlaceDetailService(client));

        user = userRepository.save(User.create("a@test.com", "테스터", null));
        place = placeRepository.save(Place.builder()
                .contentId("126508")
                .contentTypeId("12")
                .name("사천진해변")
                .category(TravelCategory.BEACH)
                .address("강원특별자치도 강릉시 사천면")
                .latitude(37.8021)
                .longitude(128.8954)
                .build());
    }

    /** 이 회원이 뽑은 슬롯 하나를 만든다. 미션 없이 뽑힌 경우다. */
    private SavedSlot savedSlotOf(User owner) {
        return savedSlotOf(owner, null);
    }

    /**
     * 미션까지 제시된 슬롯을 만든다. (결정 14)
     *
     * <p>draw가 실제로 하는 일과 같다 — 제시한 미션을 슬롯에 함께 남긴다.
     * 이 값이 있어야 조회가 <b>draw 때 보여 준 바로 그 미션</b>을 돌려줄 수 있다.
     */
    private SavedSlot savedSlotOf(User owner, Mission presented) {
        TripSession session = tripSessionRepository.save(TripSession.create(
                owner, com.lottotrip.common.enums.BudgetLevel.MEDIUM,
                TransportType.WALK, 37.7519, 128.8761));
        return savedSlotRepository.save(SavedSlot.create(session, place, presented));
    }

    private void expectDetailCall() {
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withSuccess(DETAIL_RESPONSE, MediaType.APPLICATION_JSON));
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("뽑았던 장소를 돌려준다")
    void returnsSavedPlace() {
        SavedSlot slot = savedSlotOf(user);
        expectDetailCall();

        SlotResultResponse response = slotResultService.getResult(user.getId(), slot.getId());

        assertThat(response.slotId()).isEqualTo(slot.getId());
        assertThat(response.place().placeId()).isEqualTo(place.getId());
        assertThat(response.place().name()).isEqualTo("사천진해변");
        assertThat(response.place().category()).isEqualTo("해변");
    }

    @Test
    @DisplayName("TourAPI를 실시간으로 불러 소개글을 얹는다 — 공모전 규정을 만족시키는 호출 지점이다")
    void enrichesWithLiveDetail() {
        SavedSlot slot = savedSlotOf(user);
        expectDetailCall();

        SlotResultResponse response = slotResultService.getResult(user.getId(), slot.getId());

        assertThat(response.place().liveDetailLoaded()).isTrue();
        assertThat(response.place().description()).isEqualTo("동해안의 조용한 해변입니다.");
        assertThat(response.place().homepageUrl()).isEqualTo("https://gn.go.kr");
        mockServer.verify();
    }

    @Test
    @DisplayName("공공 API가 죽어도 우리 정보는 나간다")
    void survivesTourApiFailure() {
        // 이 성질이 5-10의 핵심이다. 바깥이 멈췄다고 방금 뽑은 장소를 못 보면 안 된다.
        SavedSlot slot = savedSlotOf(user);
        mockServer.expect(requestTo(containsString("detailCommon2")))
                .andRespond(withServerError());

        SlotResultResponse response = slotResultService.getResult(user.getId(), slot.getId());

        assertThat(response.place().name()).isEqualTo("사천진해변");
        assertThat(response.place().liveDetailLoaded()).isFalse();
        assertThat(response.place().description()).isNull();
    }

    @Test
    @DisplayName("draw 때 제시했던 그 미션을 돌려준다 (결정 14)")
    void returnsMission() {
        Mission mission = missionRepository.save(
                Mission.create(place, "해변 도착 인증하기", "설명", null, 100));
        SavedSlot slot = savedSlotOf(user, mission);
        expectDetailCall();

        SlotResultResponse response = slotResultService.getResult(user.getId(), slot.getId());

        assertThat(response.mission()).isNotNull();
        assertThat(response.mission().missionId()).isEqualTo(mission.getId());
        assertThat(response.mission().title()).isEqualTo("해변 도착 인증하기");
    }

    @Test
    @DisplayName("장소에 미션이 여럿이어도 제시했던 것만 나온다 — 조회할 때마다 바뀌지 않는다")
    void returnsSameMissionOnRepeatedCalls() {
        // 결정 14 이전에는 "가장 먼저 등록된 미션"을 돌려줘서 draw가 보여 준 것과 달라질 수 있었다.
        // 이제 슬롯에 남은 것을 그대로 읽으므로, 후보가 여럿이어도 제시했던 B가 계속 나온다.
        missionRepository.save(Mission.create(place, "미션 A", "설명", null, 100));
        Mission presented = missionRepository.save(Mission.create(place, "미션 B", "설명", null, 100));
        missionRepository.save(Mission.create(place, "미션 C", "설명", null, 100));
        SavedSlot slot = savedSlotOf(user, presented);
        mockServer.expect(ExpectedCount.times(3), requestTo(containsString("detailCommon2")))
                .andRespond(withSuccess(DETAIL_RESPONSE, MediaType.APPLICATION_JSON));

        Long first = slotResultService.getResult(user.getId(), slot.getId()).mission().missionId();
        Long second = slotResultService.getResult(user.getId(), slot.getId()).mission().missionId();
        Long third = slotResultService.getResult(user.getId(), slot.getId()).mission().missionId();

        assertThat(first).isEqualTo(second).isEqualTo(third);
        assertThat(first).isEqualTo(presented.getId());
    }

    @Test
    @DisplayName("미션이 없는 장소여도 결과는 나간다")
    void allowsMissingMission() {
        SavedSlot slot = savedSlotOf(user);
        expectDetailCall();

        SlotResultResponse response = slotResultService.getResult(user.getId(), slot.getId());

        assertThat(response.place().name()).isEqualTo("사천진해변");
        assertThat(response.mission()).isNull();
    }

    // ---------- 오류 ----------

    @Test
    @DisplayName("없는 슬롯이면 RESULT_NOT_FOUND")
    void failsWhenSlotMissing() {
        assertThatThrownBy(() -> slotResultService.getResult(user.getId(), 999_999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 슬롯은 조회할 수 없다 — 있다는 사실조차 알려주지 않는다")
    void failsWhenSlotBelongsToAnotherUser() {
        // 403이 아니라 404로 답한다. 403은 "그 번호의 슬롯은 있다"를 알려 주는 셈이라,
        // 번호를 훑어 남이 무엇을 뽑았는지 세어 볼 수 있다.
        User other = userRepository.save(User.create("b@test.com", "남", null));
        SavedSlot slot = savedSlotOf(other);

        assertThatThrownBy(() -> slotResultService.getResult(user.getId(), slot.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 슬롯 조회는 바깥 API를 부르지 않는다")
    void doesNotCallTourApiForForeignSlot() {
        // 소유권을 먼저 본다. 나중에 보면 남의 슬롯 조회로도 우리 API 할당량이 깎인다.
        User other = userRepository.save(User.create("c@test.com", "남", null));
        SavedSlot slot = savedSlotOf(other);

        assertThatThrownBy(() -> slotResultService.getResult(user.getId(), slot.getId()))
                .isInstanceOf(CustomException.class);

        mockServer.verify();   // 기대를 걸지 않았으므로 호출이 있었다면 여기서 터진다
    }
}
