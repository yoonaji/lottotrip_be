package com.lottotrip.course.service;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.course.dto.CourseItemAddRequest;
import com.lottotrip.course.dto.CourseItemRemoveResponse;
import com.lottotrip.course.dto.CourseItemsResponse;
import com.lottotrip.course.dto.CourseItemResponse;
import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.course.repository.CourseItemRepository;
import com.lottotrip.course.repository.TravelCourseRepository;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.entity.UserMission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 코스에 담기 검증. (roadmap 7-1·7-2, tour_api_erd.md 4-4)
 *
 * **요청은 `slotId`만 준다.** 어떤 장소인지는 서버가 `saved_slots`에서 찾는다.
 * 프론트가 `placeId`를 보내게 하면 **뽑지도 않은 장소를 담을 수 있다.**
 *
 * **⚠️ 코스를 만드는 API가 명세에 없다.** `travel_courses.title`을 채울 입력이
 * 어디에도 없어서, `trip_sessions`처럼 **회원당 하나를 서버가 find-or-create**한다(잠정).
 *
 * DB는 진짜를 쓴다. `(course_id, place_id)` UNIQUE 제약이 실재해야
 * "같은 장소를 두 번 담을 수 없다"가 증명된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseServiceTest extends PostgresContainerSupport {

    @Autowired
    private TravelCourseRepository travelCourseRepository;
    @Autowired
    private CourseItemRepository courseItemRepository;
    @Autowired
    private SavedSlotRepository savedSlotRepository;
    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MissionRepository missionRepository;
    @Autowired
    private UserMissionRepository userMissionRepository;

    private CourseService courseService;
    private User user;
    private TripSession session;
    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        courseService = new CourseService(travelCourseRepository, courseItemRepository,
                savedSlotRepository, userRepository, userMissionRepository);

        user = userRepository.save(User.create("a@test.com", "테스터", null));
        session = tripSessionRepository.save(TripSession.create(
                user, BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761));
    }

    private Place placeNamed(String name) {
        return placeRepository.save(Place.builder()
                .contentId("c-" + (++sequence))
                .contentTypeId("12")
                .name(name)
                .category(TravelCategory.NATURE_ATTRACTION)
                .latitude(37.8021)
                .longitude(128.8954)
                .build());
    }

    /** 이 회원이 뽑은 슬롯 하나. 미션 없이 뽑힌 경우다. */
    private SavedSlot slotOf(User owner, Place place) {
        return slotOf(owner, place, null);
    }

    /**
     * 이 회원이 뽑은 슬롯 하나. `presented`가 draw 때 제시한 미션이다.
     *
     * 미션을 슬롯에 실어 두는 이유는 6-13(결정 14)과 같다 — 장소에는 미션이 여러 개라
     * 나중에 "그중 어느 것을 보여 줬는지"를 장소만으로는 복원할 수 없다.
     */
    private SavedSlot slotOf(User owner, Place place, Mission presented) {
        TripSession ownerSession = owner.getId().equals(user.getId()) ? session
                : tripSessionRepository.save(TripSession.create(
                        owner, BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761));
        return savedSlotRepository.save(SavedSlot.create(ownerSession, place, presented));
    }

    // ---------- 담기 (7-1) ----------

    @Test
    @DisplayName("슬롯을 코스에 담는다 — 장소는 slotId로 찾는다")
    void addsItemFromSlot() {
        Place place = placeNamed("사천진해변");
        SavedSlot slot = slotOf(user, place);

        CourseItemResponse response = courseService.addItem(user.getId(), new CourseItemAddRequest(slot.getId()));

        assertThat(response.itemId()).isNotNull();
        assertThat(response.place().placeId()).isEqualTo(place.getId());
        assertThat(response.place().name()).isEqualTo("사천진해변");
        assertThat(response.addedAt()).isNotNull();
    }

    @Test
    @DisplayName("코스가 없으면 만들어 준다 — 코스 생성 API가 명세에 없다")
    void createsCourseOnFirstAdd() {
        SavedSlot slot = slotOf(user, placeNamed("사천진해변"));
        assertThat(travelCourseRepository.findAll()).isEmpty();

        courseService.addItem(user.getId(), new CourseItemAddRequest(slot.getId()));

        assertThat(travelCourseRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("두 번째부터는 같은 코스에 쌓인다 — 담을 때마다 코스가 늘면 안 된다")
    void reusesExistingCourse() {
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("A")).getId()));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("B")).getId()));

        assertThat(travelCourseRepository.findAll()).hasSize(1);
        assertThat(courseItemRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("회원이 다르면 코스도 따로 만든다")
    void separatesCoursePerUser() {
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("A")).getId()));

        courseService.addItem(other.getId(), new CourseItemAddRequest(slotOf(other, placeNamed("B")).getId()));

        assertThat(travelCourseRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("순번은 1부터 담은 차례대로 붙는다")
    void numbersSequenceFromOne() {
        // 순번이 없으면 코스 조회에서 담은 순서를 재현할 수 없다.
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("A")).getId()));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("B")).getId()));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("C")).getId()));

        Long courseId = travelCourseRepository.findAll().get(0).getId();
        assertThat(courseItemRepository.findByCourseIdOrderBySequenceAsc(courseId))
                .extracting(item -> item.getPlace().getName(), CourseItem::getSequence)
                .containsExactly(tuple("A", 1), tuple("B", 2), tuple("C", 3));
    }

    // ---------- 담을 수 없는 슬롯 ----------

    @Test
    @DisplayName("없는 슬롯이면 RESULT_NOT_FOUND")
    void failsWhenSlotMissing() {
        assertThatThrownBy(() -> courseService.addItem(user.getId(), new CourseItemAddRequest(999_999L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 슬롯도 RESULT_NOT_FOUND — 있다는 사실조차 알려주지 않는다")
    void failsWhenSlotBelongsToOther() {
        // 403이면 "그 번호의 슬롯은 존재한다"를 알려 주는 셈이라,
        // 번호를 훑어 남이 무엇을 얼마나 뽑았는지 세어 볼 수 있다. 슬롯 조회(6-7)와 같은 원칙이다.
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        SavedSlot othersSlot = slotOf(other, placeNamed("남의 장소"));

        assertThatThrownBy(() -> courseService.addItem(user.getId(), new CourseItemAddRequest(othersSlot.getId())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    // ---------- 중복 방지 (7-2) ----------

    @Test
    @DisplayName("같은 장소를 또 담으면 ALREADY_ADDED")
    void failsWhenPlaceAlreadyAdded() {
        Place place = placeNamed("사천진해변");
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place).getId()));

        SavedSlot again = slotOf(user, place);
        assertThatThrownBy(() -> courseService.addItem(user.getId(), new CourseItemAddRequest(again.getId())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_ADDED);
    }

    @Test
    @DisplayName("슬롯이 달라도 장소가 같으면 막는다 — 같은 곳을 여러 번 뽑을 수 있다")
    void blocksSamePlaceFromDifferentSlots() {
        // 온디맨드 추첨이라 인기 있는 장소는 반복해서 뽑힌다. 슬롯 번호로만 막으면
        // 같은 장소가 코스에 여러 줄로 쌓인다. 중복 판정 기준은 장소여야 한다.
        Place place = placeNamed("사천진해변");
        SavedSlot first = slotOf(user, place);
        SavedSlot second = slotOf(user, place);
        assertThat(first.getId()).isNotEqualTo(second.getId());

        courseService.addItem(user.getId(), new CourseItemAddRequest(first.getId()));

        assertThatThrownBy(() -> courseService.addItem(user.getId(), new CourseItemAddRequest(second.getId())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_ADDED);
    }

    @Test
    @DisplayName("중복이면 항목도 순번도 늘지 않는다")
    void addsNothingWhenDuplicated() {
        Place place = placeNamed("사천진해변");
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place).getId()));

        SavedSlot again = slotOf(user, place);
        assertThatThrownBy(() -> courseService.addItem(user.getId(), new CourseItemAddRequest(again.getId())))
                .isInstanceOf(CustomException.class);

        assertThat(courseItemRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("다른 회원은 같은 장소를 담을 수 있다 — 코스가 다르면 남남이다")
    void allowsSamePlaceForDifferentUsers() {
        Place place = placeNamed("사천진해변");
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place).getId()));

        courseService.addItem(other.getId(), new CourseItemAddRequest(slotOf(other, place).getId()));

        assertThat(courseItemRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("담았다 지운 장소는 다시 담을 수 있다")
    void allowsReAddAfterRemoval() {
        // 지운 뒤에도 막히면 사용자가 되돌릴 방법이 없다.
        Place place = placeNamed("사천진해변");
        CourseItemResponse added = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slotOf(user, place).getId()));
        courseItemRepository.deleteById(added.itemId());
        courseItemRepository.flush();

        CourseItemResponse readded = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slotOf(user, place).getId()));

        assertThat(readded.itemId()).isNotNull();
        assertThat(courseItemRepository.findAll()).hasSize(1);
    }

    // ---------- 코스 조회 (7-3) ----------

    @Test
    @DisplayName("담은 순서대로 돌려준다")
    void listsItemsInAddedOrder() {
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("A")).getId()));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("B")).getId()));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("C")).getId()));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items())
                .extracting(item -> item.place().name())
                .containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("한 번도 담지 않았으면 빈 목록이다 — 코스가 없는 것이 오류는 아니다")
    void returnsEmptyWhenNothingAdded() {
        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items()).isEmpty();
        // 조회만으로 코스를 만들지는 않는다. 담을 때 만든다.
        assertThat(travelCourseRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("남의 코스는 보이지 않는다")
    void hidesOtherUsersItems() {
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        courseService.addItem(other.getId(), new CourseItemAddRequest(slotOf(other, placeNamed("남의 장소")).getId()));

        assertThat(courseService.getItems(user.getId()).items()).isEmpty();
    }

    @Test
    @DisplayName("슬롯이 제시한 미션을 함께 준다")
    void includesMission() {
        Place place = placeNamed("사천진해변");
        Mission mission = missionRepository.save(Mission.create(place, "해변 도착 인증하기", "설명", null, 100));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place, mission).getId()));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).mission().missionId()).isEqualTo(mission.getId());
    }

    @Test
    @DisplayName("장소에 미션이 여럿이어도 draw 때 제시한 그 미션을 준다")
    void includesTheMissionPresentedAtDraw() {
        // 이것이 course_items.slot_id를 붙인 이유다. 장소는 미션을 3개까지 갖는데
        // (MissionMatcher.REQUIRED_MISSION_COUNT), 코스가 장소만 가리키면 그중 무엇을
        // 보여 줬는지 알 수 없어 엉뚱한 미션이 나간다. 6-7이 겪은 것과 같은 문제다.
        Place place = placeNamed("사천진해변");
        missionRepository.save(Mission.create(place, "미션 A", "설명", null, 100));
        Mission presented = missionRepository.save(Mission.create(place, "미션 B", "설명", null, 100));
        missionRepository.save(Mission.create(place, "미션 C", "설명", null, 100));

        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place, presented).getId()));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items().get(0).mission().missionId()).isEqualTo(presented.getId());
    }

    @Test
    @DisplayName("완료한 미션은 completed=true다")
    void reportsCompletedMission() {
        // 판정 근거는 user_missions에 줄이 있는가뿐이다. 그 줄은 GPS 인증을 통과했을 때만 생긴다(8-1·8-2).
        Place place = placeNamed("사천진해변");
        Mission mission = missionRepository.save(Mission.create(place, "해변 도착 인증하기", "설명", null, 100));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place, mission).getId()));

        userMissionRepository.save(UserMission.complete(user, mission));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items().get(0).mission().completed()).isTrue();
    }

    @Test
    @DisplayName("아직 완료하지 않았으면 false다")
    void reportsIncompleteMission() {
        Place place = placeNamed("사천진해변");
        Mission mission = missionRepository.save(Mission.create(place, "해변 도착 인증하기", "설명", null, 100));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place, mission).getId()));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items().get(0).mission().completed()).isFalse();
    }

    @Test
    @DisplayName("다른 사람이 완료한 것은 내 코스에 반영되지 않는다")
    void ignoresAnotherUsersCompletion() {
        // 미션은 장소에 붙은 공용 자산이라 여러 사람이 각자 완료한다(8-2).
        // 회원을 구분하지 않으면 남이 다녀온 곳이 내 코스에서 완료로 보인다.
        Place place = placeNamed("사천진해변");
        Mission mission = missionRepository.save(Mission.create(place, "해변 도착 인증하기", "설명", null, 100));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place, mission).getId()));

        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        userMissionRepository.save(UserMission.complete(other, mission));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items().get(0).mission().completed()).isFalse();
    }

    @Test
    @DisplayName("항목이 여러 개면 각자의 완료 여부가 매겨진다")
    void marksEachItemIndependently() {
        // 완료 여부를 한 번에 조회하므로(N+1 회피), 항목별로 제대로 갈리는지 확인한다.
        Place done = placeNamed("사천진해변");
        Mission doneMission = missionRepository.save(Mission.create(done, "해변 인증하기", "설명", null, 100));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, done, doneMission).getId()));

        Place notYet = placeNamed("순포습지");
        Mission pending = missionRepository.save(Mission.create(notYet, "습지 인증하기", "설명", null, 100));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, notYet, pending).getId()));

        userMissionRepository.save(UserMission.complete(user, doneMission));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items()).extracting(item -> item.mission().completed())
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("슬롯이 미션 없이 뽑혔으면 목록에는 나오되 미션은 비어 있다")
    void listsItemWithoutMission() {
        // 미션은 곁들이는 정보다. 없다고 담은 장소가 목록에서 사라지면 안 된다.
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("미션 없는 곳")).getId()));

        CourseItemsResponse response = courseService.getItems(user.getId());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).mission()).isNull();
    }

    @Test
    @DisplayName("장소에 미션이 있어도 슬롯이 제시하지 않았으면 비워 둔다")
    void doesNotFallBackToPlaceMissions() {
        // 폴백하지 않는다. 슬롯의 미션이 비었다는 것은 draw 때 MissionMatcher가 하나도
        // 확보하지 못했다는 뜻이고, 여기서 장소의 아무 미션이나 끼워 넣으면
        // "사용자가 본 적 없는 미션"이 코스에 나타난다.
        Place place = placeNamed("사천진해변");
        missionRepository.save(Mission.create(place, "나중에 생긴 미션", "설명", null, 100));

        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, place).getId()));

        assertThat(courseService.getItems(user.getId()).items().get(0).mission()).isNull();
    }

    @Test
    @DisplayName("담은 항목에서 슬롯을 거쳐 뽑을 당시의 정보에 닿는다")
    void itemKeepsLinkToSlot() {
        // slot_id 하나로 미션뿐 아니라 세션(이동수단·예산·뽑은 시각)까지 따라갈 수 있다.
        Place place = placeNamed("사천진해변");
        SavedSlot slot = slotOf(user, place);
        CourseItemResponse added = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slot.getId()));

        CourseItem item = courseItemRepository.findById(added.itemId()).orElseThrow();

        assertThat(item.getSlot().getId()).isEqualTo(slot.getId());
        assertThat(item.getSlot().getSession().getTransportation()).isEqualTo(TransportType.WALK);
    }

    // ---------- 코스 항목 삭제 (7-4) ----------

    @Test
    @DisplayName("담은 항목을 지운다")
    void removesItem() {
        CourseItemResponse added = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("사천진해변")).getId()));

        CourseItemRemoveResponse response = courseService.removeItem(user.getId(), added.itemId());

        assertThat(response.itemId()).isEqualTo(added.itemId());
        assertThat(response.deleted()).isTrue();
        assertThat(courseItemRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("없는 항목이면 ITEM_NOT_FOUND")
    void failsWhenItemMissing() {
        assertThatThrownBy(() -> courseService.removeItem(user.getId(), 999_999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 항목도 ITEM_NOT_FOUND — 남의 코스를 지울 수 없다")
    void failsWhenItemBelongsToOther() {
        User other = userRepository.save(User.create("b@test.com", "다른 사람", null));
        CourseItemResponse othersItem = courseService.addItem(
                other.getId(), new CourseItemAddRequest(slotOf(other, placeNamed("남의 장소")).getId()));

        assertThatThrownBy(() -> courseService.removeItem(user.getId(), othersItem.itemId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ITEM_NOT_FOUND);
        assertThat(courseItemRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("지워도 남은 항목의 순번은 그대로다")
    void keepsSequenceAfterRemoval() {
        // 순번을 다시 매기면 "3개 중 2번을 지웠다"는 사실이 사라진다.
        // 남은 것의 순번이 1, 3이어도 순서는 그대로 재현된다.
        CourseItemResponse a = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("A")).getId()));
        CourseItemResponse b = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("B")).getId()));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("C")).getId()));

        courseService.removeItem(user.getId(), b.itemId());

        Long courseId = travelCourseRepository.findAll().get(0).getId();
        assertThat(courseItemRepository.findByCourseIdOrderBySequenceAsc(courseId))
                .extracting(item -> item.getPlace().getName(), CourseItem::getSequence)
                .containsExactly(tuple("A", 1), tuple("C", 3));
        assertThat(a.itemId()).isNotNull();
    }

    @Test
    @DisplayName("가운데를 지운 뒤 새로 담아도 순번이 겹치지 않는다")
    void neverReusesSequenceOfSurvivingItem() {
        // 이것이 순번을 "개수 + 1"이 아니라 "마지막 순번 + 1"로 채우는 이유다.
        // 1·2·3에서 2를 지우면 개수는 2가 되는데, 개수로 채번하면 새 항목이 3번이 되어
        // 살아 있는 C(3번)와 겹친다.
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("A")).getId()));
        CourseItemResponse b = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("B")).getId()));
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("C")).getId()));
        courseService.removeItem(user.getId(), b.itemId());

        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("D")).getId()));

        Long courseId = travelCourseRepository.findAll().get(0).getId();
        assertThat(courseItemRepository.findByCourseIdOrderBySequenceAsc(courseId))
                .extracting(item -> item.getPlace().getName(), CourseItem::getSequence)
                .containsExactly(tuple("A", 1), tuple("C", 3), tuple("D", 4));
    }

    @Test
    @DisplayName("마지막을 지우면 그 순번은 다시 쓰인다 — 겹치지 않으므로 문제없다")
    void reusesSequenceOfRemovedTail() {
        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("A")).getId()));
        CourseItemResponse b = courseService.addItem(
                user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("B")).getId()));
        courseService.removeItem(user.getId(), b.itemId());

        courseService.addItem(user.getId(), new CourseItemAddRequest(slotOf(user, placeNamed("C")).getId()));

        Long courseId = travelCourseRepository.findAll().get(0).getId();
        assertThat(courseItemRepository.findByCourseIdOrderBySequenceAsc(courseId))
                .extracting(item -> item.getPlace().getName(), CourseItem::getSequence)
                .containsExactly(tuple("A", 1), tuple("C", 2));
    }

    @Test
    @DisplayName("담기에 실패하면 코스도 만들지 않는다")
    void createsNothingWhenSlotMissing() {
        assertThatThrownBy(() -> courseService.addItem(user.getId(), new CourseItemAddRequest(999_999L)))
                .isInstanceOf(CustomException.class);

        assertThat(travelCourseRepository.findAll()).isEmpty();
        assertThat(courseItemRepository.findAll()).isEmpty();
    }
}
