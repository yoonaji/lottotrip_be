package com.lottotrip.course.repository;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.course.entity.TravelCourse;
import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코스 조회 검증. (tour_api_erd.md 1 — course_items, 4-4)
 *
 * <p>코스에 담을 때 두 가지를 조회해야 한다.
 * ① 이미 담긴 장소인가({@code ALREADY_ADDED}) ② 다음 순번은 몇 번인가
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private CourseItemRepository courseItemRepository;

    @Autowired
    private TravelCourseRepository travelCourseRepository;

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

    private TravelCourse persistedCourse(User user) {
        TravelCourse course = TravelCourse.create(user, "나의 강릉 여행");
        entityManager.persist(course);
        return course;
    }

    @Test
    @DisplayName("회원의 코스를 찾는다")
    void findsCourseByUser() {
        User user = persistedUser();
        TravelCourse course = persistedCourse(user);
        entityManager.flush();

        var found = travelCourseRepository.findByUserId(user.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(course.getId());
    }

    @Test
    @DisplayName("이미 담긴 장소인지 확인한다")
    void checksAlreadyAdded() {
        // 명세의 409 ALREADY_ADDED 판정에 쓴다. (tour_api_erd.md 4-4)
        TravelCourse course = persistedCourse(persistedUser());
        Place added = persistedPlace("사천진해변");
        Place notAdded = persistedPlace("주문진항");
        entityManager.persist(CourseItem.create(course, added, 1));
        entityManager.flush();

        assertThat(courseItemRepository.existsByCourseIdAndPlaceId(course.getId(), added.getId())).isTrue();
        assertThat(courseItemRepository.existsByCourseIdAndPlaceId(course.getId(), notAdded.getId())).isFalse();
    }

    @Test
    @DisplayName("비어 있는 코스의 마지막 순번은 없다")
    void maxSequenceIsNullWhenEmpty() {
        // 첫 항목의 sequence를 1로 정하려면 "아직 아무것도 없음"을 구분할 수 있어야 한다.
        TravelCourse course = persistedCourse(persistedUser());
        entityManager.flush();

        assertThat(courseItemRepository.findMaxSequence(course.getId())).isNull();
    }

    @Test
    @DisplayName("코스의 마지막 순번을 찾는다")
    void findsMaxSequence() {
        // 명세: sequence는 해당 코스의 마지막 순번 + 1로 채운다. (tour_api_erd.md 1)
        TravelCourse course = persistedCourse(persistedUser());
        entityManager.persist(CourseItem.create(course, persistedPlace("사천진해변"), 1));
        entityManager.persist(CourseItem.create(course, persistedPlace("주문진항"), 2));
        entityManager.flush();

        assertThat(courseItemRepository.findMaxSequence(course.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 순번은 코스별로 따로 센다")
    void maxSequenceIsPerCourse() {
        User user = persistedUser();
        TravelCourse mine = persistedCourse(user);
        TravelCourse other = persistedCourse(persistedUser());
        entityManager.persist(CourseItem.create(other, persistedPlace("주문진항"), 5));
        entityManager.flush();

        // 남의 코스 순번이 내 코스에 영향을 주면 안 된다.
        assertThat(courseItemRepository.findMaxSequence(mine.getId())).isNull();
    }

    @Test
    @DisplayName("코스에 담긴 항목을 순번대로 조회한다")
    void findsItemsInSequenceOrder() {
        // GET /course/items 응답 목록에 쓴다. 담은 순서대로 보여야 한다.
        TravelCourse course = persistedCourse(persistedUser());
        entityManager.persist(CourseItem.create(course, persistedPlace("주문진항"), 2));
        entityManager.persist(CourseItem.create(course, persistedPlace("사천진해변"), 1));
        entityManager.flush();
        entityManager.clear();

        var items = courseItemRepository.findByCourseIdOrderBySequenceAsc(course.getId());

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getPlace().getName()).isEqualTo("사천진해변");
        assertThat(items.get(1).getPlace().getName()).isEqualTo("주문진항");
    }
}
