package com.lottotrip.course.entity;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.slot.entity.SavedSlot;
import com.lottotrip.slot.entity.TransportType;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `travel_courses` / `course_items` 테이블 매핑 검증.
 * (tour_api_erd.md 1 — travel_courses / course_items)
 *
 * 코스는 회원이 슬롯에서 뽑은 장소를 담아 두는 목록이다.
 * 담긴 장소 하나가 `course_items` 한 줄이 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseEntityTest extends PostgresContainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    private User persistedUser() {
        User user = User.create("test@khu.ac.kr", "주노", null);
        entityManager.persist(user);
        return user;
    }

    /** 한 테스트에서 장소를 여러 개 만들 수 있다. content_id는 UNIQUE라 매번 다른 값이어야 한다. */
    private int contentSeq = 0;

    private Place persistedPlace(String name) {
        State state = State.create("강원특별자치도");
        entityManager.persist(state);
        City city = City.create(state, "강릉시");
        entityManager.persist(city);
        Place place = Place.builder()
                .contentId("TEST-" + (++contentSeq))
                .city(city)
                .name(name)
                .category(TravelCategory.NATURE_ATTRACTION)
                .address("강원 강릉시")
                .latitude(37.8021)
                .longitude(128.8954)
                .budgetTier(BudgetLevel.LOW)
                .publicTransportWeight(3)
                .build();
        entityManager.persist(place);
        return place;
    }

    private TravelCourse persistedCourse() {
        TravelCourse course = TravelCourse.create(persistedUser(), "나의 강릉 여행");
        entityManager.persist(course);
        return course;
    }

    /** 이 장소를 뽑은 슬롯. 코스 항목은 반드시 슬롯을 거쳐 만들어진다. */
    private SavedSlot persistedSlot(Place place) {
        return persistedSlot(place, null);
    }

    private SavedSlot persistedSlot(Place place, Mission presented) {
        TripSession session = TripSession.create(
                persistedUser(), BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761);
        entityManager.persist(session);
        SavedSlot slot = SavedSlot.create(session, place, presented);
        entityManager.persist(slot);
        return slot;
    }

    @Test
    @DisplayName("코스를 저장하면 courseId와 createdAt이 자동으로 채워진다")
    void assignsCourseIdAndCreatedAt() {
        TravelCourse course = TravelCourse.create(persistedUser(), "나의 강릉 여행");

        entityManager.persist(course);
        entityManager.flush();

        assertThat(course.getId()).isNotNull();
        assertThat(course.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("코스는 소유한 회원을 함께 조회할 수 있다")
    void courseBelongsToUser() {
        User user = persistedUser();
        TravelCourse course = TravelCourse.create(user, "나의 강릉 여행");
        entityManager.persist(course);
        entityManager.flush();
        entityManager.clear();

        TravelCourse found = entityManager.find(TravelCourse.class, course.getId());
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getTitle()).isEqualTo("나의 강릉 여행");
    }

    @Test
    @DisplayName("코스에 장소를 담으면 itemId와 addedAt이 자동으로 채워진다")
    void assignsItemIdAndAddedAt() {
        CourseItem item = CourseItem.create(persistedCourse(), persistedSlot(persistedPlace("사천진해변")), 1);

        entityManager.persist(item);
        entityManager.flush();

        assertThat(item.getId()).isNotNull();
        assertThat(item.getAddedAt()).isNotNull();
    }

    @Test
    @DisplayName("담긴 항목은 코스와 장소를 함께 조회할 수 있다")
    void itemLinksCourseAndPlace() {
        TravelCourse course = persistedCourse();
        CourseItem item = CourseItem.create(course, persistedSlot(persistedPlace("사천진해변")), 1);
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        CourseItem found = entityManager.find(CourseItem.class, item.getId());
        assertThat(found.getCourse().getId()).isEqualTo(course.getId());
        assertThat(found.getPlace().getName()).isEqualTo("사천진해변");
    }

    @Test
    @DisplayName("담긴 항목은 뽑은 슬롯과 그때 제시한 미션까지 따라갈 수 있다")
    void itemLinksSlotAndPresentedMission() {
        // course_items가 place_id만 가리키면 장소의 미션 여러 개 중 무엇을 보여 줬는지 알 수 없다.
        // slot_id를 거치면 saved_slots.mission_id 한 개로 좁혀진다. (6-13 결정 14와 같은 해법)
        Place place = persistedPlace("사천진해변");
        Mission presented = Mission.create(place, "해변 도착 인증하기", "설명", null, 100);
        entityManager.persist(presented);
        SavedSlot slot = persistedSlot(place, presented);

        CourseItem item = CourseItem.create(persistedCourse(), slot, 1);
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        CourseItem found = entityManager.find(CourseItem.class, item.getId());
        assertThat(found.getSlot().getId()).isEqualTo(slot.getId());
        assertThat(found.getSlot().getMission().getTitle()).isEqualTo("해변 도착 인증하기");
    }

    @Test
    @DisplayName("장소는 슬롯에서 가져온다 — 항목의 장소와 슬롯의 장소는 어긋날 수 없다")
    void takesPlaceFromSlot() {
        // create가 place를 따로 받지 않는 이유다. 둘 다 받으면 서로 다른 값을 넘길 수 있고,
        // 그러면 "코스에 담긴 장소"와 "실제로 뽑은 장소"가 갈라진다.
        Place place = persistedPlace("사천진해변");
        SavedSlot slot = persistedSlot(place);

        CourseItem item = CourseItem.create(persistedCourse(), slot, 1);

        assertThat(item.getPlace().getId()).isEqualTo(place.getId());
    }

    @Test
    @DisplayName("slot_id는 NOT NULL이다 — 슬롯을 거치지 않은 항목은 있을 수 없다")
    void requiresSlot() {
        // 담기 API가 slotId를 필수로 받으므로 슬롯 없는 항목은 만들어질 수 없다.
        // nullable로 두면 DB가 그 있을 수 없는 상태를 허용하게 된다.
        String nullable = (String) entityManager.getEntityManager()
                .createNativeQuery("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_name = 'course_items' AND column_name = 'slot_id'
                        """)
                .getSingleResult();

        assertThat(nullable).isEqualTo("NO");
    }

    @Test
    @DisplayName("sequence로 코스에 담은 순서를 보관한다")
    void keepsSequence() {
        // 명세: sequence는 해당 코스의 마지막 순번 + 1로 채운다. (tour_api_erd.md 1)
        TravelCourse course = persistedCourse();

        entityManager.persist(CourseItem.create(course, persistedSlot(persistedPlace("사천진해변")), 1));
        entityManager.persist(CourseItem.create(course, persistedSlot(persistedPlace("주문진항")), 2));
        entityManager.flush();
        entityManager.clear();

        Long count = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(i) FROM CourseItem i WHERE i.course.id = :id AND i.sequence = 2", Long.class)
                .setParameter("id", course.getId())
                .getSingleResult();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 코스에 같은 장소를 두 번 담을 수 없다")
    void rejectsDuplicatePlaceInSameCourse() {
        // 명세의 409 ALREADY_ADDED("이미 코스에 담긴 항목")를 DB 차원에서도 보장한다.
        // 서비스에서 조회 후 저장하는 방식만으로는 동시에 두 번 요청이 오면 둘 다 통과할 수 있다.
        TravelCourse course = persistedCourse();
        Place place = persistedPlace("사천진해변");
        entityManager.persist(CourseItem.create(course, persistedSlot(place), 1));

        // 슬롯이 달라도 장소가 같으면 막힌다. 온디맨드 추첨이라 인기 있는 곳은 반복해서 뽑힌다.
        assertThatThrownBy(() -> entityManager.persist(CourseItem.create(course, persistedSlot(place), 2)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_course_items_course_place");
    }

    @Test
    @DisplayName("코스가 다르면 같은 장소를 담을 수 있다")
    void allowsSamePlaceInDifferentCourse() {
        Place place = persistedPlace("사천진해변");

        entityManager.persist(CourseItem.create(persistedCourse(), persistedSlot(place), 1));
        entityManager.persist(CourseItem.create(persistedCourse(), persistedSlot(place), 1));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }
}
