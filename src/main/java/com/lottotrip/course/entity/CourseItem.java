package com.lottotrip.course.entity;

import com.lottotrip.place.entity.Place;
import com.lottotrip.slot.entity.SavedSlot;
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

    /**
     * 이 항목을 담게 된 슬롯. (roadmap 7-6)
     *
     * **왜 장소만으로는 부족한가.** 장소 하나에는 미션이 여러 개 붙어 있다
     * (`MissionMatcher.REQUIRED_MISSION_COUNT` = 3). `place_id`만 가리키면
     * **draw 때 사용자에게 제시했던 미션이 그중 어느 것이었는지 복원할 방법이 없다.**
     * 슬롯을 거치면 `saved_slots.mission_id` 한 개로 좁혀진다(6-13 결정 14와 같은 해법).
     *
     * 미션 외에 세션(이동수단·예산·뽑은 시각)까지 따라갈 수 있는 것은 덤이다.
     *
     * **NOT NULL인 이유:** 담기 API가 `slotId`를 필수로 받으므로
     * 슬롯을 거치지 않은 항목은 만들어질 수 없다. nullable로 두면 DB가 그 있을 수 없는 상태를 허용한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private SavedSlot slot;

    /**
     * 담긴 장소. 항상 {@link #slot}의 장소와 같다.
     *
     * **슬롯으로 대신할 수 있는데도 남겨 둔 이유:** 중복 방지 UNIQUE 제약이 이 컬럼을 쓴다.
     * 같은 장소를 다른 슬롯으로 다시 뽑는 일이 흔해서, 슬롯 기준으로 막으면
     * **같은 장소가 코스에 여러 줄로 쌓인다.**
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /**
     * 코스 안에서의 순번. 담을 때 "그 코스의 마지막 순번 + 1"로 채운다. (tour_api_erd.md 1)
     *
     * 컬럼명을 따옴표로 감싼 이유: `sequence`는 SQL 키워드라 그냥 두면
     * DB에 따라 문법 오류가 날 수 있다.
     */
    @Column(name = "\"sequence\"", nullable = false)
    private Integer sequence;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;

    private CourseItem(TravelCourse course, SavedSlot slot, Integer sequence) {
        this.course = course;
        this.slot = slot;
        this.place = slot.getPlace();
        this.sequence = sequence;
    }

    /**
     * 슬롯을 코스 항목으로 만든다.
     *
     * **장소를 따로 받지 않고 슬롯에서 꺼내는 이유:** 둘 다 받으면 서로 다른 값을 넘길 수 있고,
     * 그러면 "코스에 담긴 장소"와 "실제로 뽑은 장소"가 갈라진다. 인자로 받지 않으면 그럴 수가 없다.
     */
    public static CourseItem create(TravelCourse course, SavedSlot slot, Integer sequence) {
        return new CourseItem(course, slot, sequence);
    }
}
