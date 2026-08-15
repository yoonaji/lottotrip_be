package com.lottotrip.course.service;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.course.dto.CourseItemAddRequest;
import com.lottotrip.course.dto.CourseItemResponse;
import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.course.repository.CourseItemRepository;
import com.lottotrip.course.repository.TravelCourseRepository;
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
 * <p><b>요청은 {@code slotId}만 준다.</b> 어떤 장소인지는 서버가 {@code saved_slots}에서 찾는다.
 * 프론트가 {@code placeId}를 보내게 하면 <b>뽑지도 않은 장소를 담을 수 있다.</b>
 *
 * <p><b>⚠️ 코스를 만드는 API가 명세에 없다.</b> {@code travel_courses.title}을 채울 입력이
 * 어디에도 없어서, {@code trip_sessions}처럼 <b>회원당 하나를 서버가 find-or-create</b>한다(잠정).
 *
 * <p>DB는 진짜를 쓴다. {@code (course_id, place_id)} UNIQUE 제약이 실재해야
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

    private CourseService courseService;
    private User user;
    private TripSession session;
    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        courseService = new CourseService(
                travelCourseRepository, courseItemRepository, savedSlotRepository, userRepository);

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

    /** 이 회원이 뽑은 슬롯 하나. 코스에 담을 수 있는 상태다. */
    private SavedSlot slotOf(User owner, Place place) {
        TripSession ownerSession = owner.getId().equals(user.getId()) ? session
                : tripSessionRepository.save(TripSession.create(
                        owner, BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761));
        return savedSlotRepository.save(SavedSlot.create(ownerSession, place, null));
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

    @Test
    @DisplayName("담기에 실패하면 코스도 만들지 않는다")
    void createsNothingWhenSlotMissing() {
        assertThatThrownBy(() -> courseService.addItem(user.getId(), new CourseItemAddRequest(999_999L)))
                .isInstanceOf(CustomException.class);

        assertThat(travelCourseRepository.findAll()).isEmpty();
        assertThat(courseItemRepository.findAll()).isEmpty();
    }
}
