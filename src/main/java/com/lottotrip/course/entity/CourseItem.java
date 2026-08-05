package com.lottotrip.course.entity;

import com.lottotrip.place.entity.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 코스에 담긴 장소 한 건. (tour_api_erd.md 1 — course_items)
 */
@Entity
@Table(
        name = "course_items",
        // 명세의 409 ALREADY_ADDED를 DB 차원에서도 보장한다.
        // 서비스에서 "이미 있나 조회 → 없으면 저장"만 하면, 같은 요청이 동시에 두 번 들어올 때
        // 둘 다 조회를 통과해 둘 다 저장될 수 있다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_items_course_place",
                columnNames = {"course_id", "place_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private TravelCourse course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /**
     * 코스 안에서의 순번. 담을 때 "그 코스의 마지막 순번 + 1"로 채운다. (tour_api_erd.md 1)
     *
     * <p>컬럼명을 따옴표로 감싼 이유: {@code sequence}는 SQL 키워드라 그냥 두면
     * DB에 따라 문법 오류가 날 수 있다.
     */
    @Column(name = "\"sequence\"", nullable = false)
    private Integer sequence;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;

    private CourseItem(TravelCourse course, Place place, Integer sequence) {
        this.course = course;
        this.place = place;
        this.sequence = sequence;
    }

    public static CourseItem create(TravelCourse course, Place place, Integer sequence) {
        return new CourseItem(course, place, sequence);
    }
}
