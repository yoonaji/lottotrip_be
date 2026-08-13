package com.lottotrip.course.entity;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
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
 * {@code travel_courses} / {@code course_items} 테이블 매핑 검증.
 * (tour_api_erd.md 1 — travel_courses / course_items)
 *
 * <p>코스는 회원이 슬롯에서 뽑은 장소를 담아 두는 목록이다.
 * 담긴 장소 하나가 {@code course_items} 한 줄이 된다.
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
                .category(TravelCategory.BEACH)
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
        CourseItem item = CourseItem.create(persistedCourse(), persistedPlace("사천진해변"), 1);

        entityManager.persist(item);
        entityManager.flush();

        assertThat(item.getId()).isNotNull();
        assertThat(item.getAddedAt()).isNotNull();
    }

    @Test
    @DisplayName("담긴 항목은 코스와 장소를 함께 조회할 수 있다")
    void itemLinksCourseAndPlace() {
        TravelCourse course = persistedCourse();
        CourseItem item = CourseItem.create(course, persistedPlace("사천진해변"), 1);
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        CourseItem found = entityManager.find(CourseItem.class, item.getId());
        assertThat(found.getCourse().getId()).isEqualTo(course.getId());
        assertThat(found.getPlace().getName()).isEqualTo("사천진해변");
    }

    @Test
    @DisplayName("sequence로 코스에 담은 순서를 보관한다")
    void keepsSequence() {
        // 명세: sequence는 해당 코스의 마지막 순번 + 1로 채운다. (tour_api_erd.md 1)
        TravelCourse course = persistedCourse();

        entityManager.persist(CourseItem.create(course, persistedPlace("사천진해변"), 1));
        entityManager.persist(CourseItem.create(course, persistedPlace("주문진항"), 2));
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
        entityManager.persist(CourseItem.create(course, place, 1));

        assertThatThrownBy(() -> entityManager.persist(CourseItem.create(course, place, 2)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_course_items_course_place");
    }

    @Test
    @DisplayName("코스가 다르면 같은 장소를 담을 수 있다")
    void allowsSamePlaceInDifferentCourse() {
        Place place = persistedPlace("사천진해변");

        entityManager.persist(CourseItem.create(persistedCourse(), place, 1));
        entityManager.persist(CourseItem.create(persistedCourse(), place, 1));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }
}
