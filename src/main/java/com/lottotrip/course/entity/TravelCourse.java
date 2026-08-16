package com.lottotrip.course.entity;

import com.lottotrip.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 여행 코스. (tour_api_erd.md 1 — travel_courses)
 *
 * 회원이 슬롯에서 뽑은 장소를 담아 두는 목록이다.
 *
 * ⚠️ 코스를 만드는 API가 명세에 없고 `GET /course/items`도 코스 id를 받지 않는다.
 * 회원당 코스 하나를 서버가 알아서 찾거나 만드는(find-or-create) 구조로 보이며,
 * 그 판단은 7단계에서 확정한다. Entity는 어느 쪽이든 그대로 쓸 수 있다.
 */
@Entity
@Table(name = "travel_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private TravelCourse(User user, String title) {
        this.user = user;
        this.title = title;
    }

    public static TravelCourse create(User user, String title) {
        return new TravelCourse(user, title);
    }
}
