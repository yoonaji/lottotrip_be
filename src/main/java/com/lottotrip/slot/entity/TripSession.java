package com.lottotrip.slot.entity;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 여행 세션. (tour_api_erd.md 1 — trip_sessions, 결정 1)
 *
 * 프론트는 세션의 존재를 모른다. 서버가 회원 기준으로 12시간 이내 세션을 찾아 재사용하고
 * 없으면 새로 만든다(find-or-create). 그 판정은 6-1에서 구현한다.
 *
 * 여기 저장된 예산·이동수단·좌표는 그 세션의 첫 슬롯 기준 참고값이다.
 * 같은 세션에서 조건을 바꿔 다시 돌려도 이 값들은 갱신하지 않는다(결정 1의 A안).
 * 그래서 값을 바꾸는 메서드를 두지 않았다.
 */
@Entity
@Table(
        name = "trip_sessions",
        // 세션 재사용 판정은 "이 회원의 가장 최근 세션"을 찾는 조회로 시작한다.
        // 슬롯을 돌릴 때마다 실행되므로 인덱스를 걸어 둔다.
        indexes = @Index(name = "idx_trip_sessions_user_created", columnList = "user_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_range", nullable = false, length = 20)
    private BudgetLevel budgetRange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportType transportation;

    /**
     * ERD의 `accommodation_coord POINT`를 위도·경도로 나눈 것. (결정 5)
     *
     * ⚠️ nullable인 이유는 값이 없어도 돼서가 아니라 탈퇴 시 지워지기 때문이다(9-5, 결정 20).
     * 숙소 좌표는 위치정보라 파기 대상이다. 세션을 만들 때는 {@link #create}가 값을 요구한다.
     */
    @Column(name = "accommodation_latitude")
    private Double accommodationLatitude;

    @Column(name = "accommodation_longitude")
    private Double accommodationLongitude;

    /** 요청으로 받지 않고 이동수단에서 계산해 넣는다. (결정 2) */
    @Column(name = "search_radius_km", nullable = false)
    private Integer searchRadiusKm;

    /** 세션이 아직 유효한지(12시간 이내인지) 판단하는 기준이다. */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private TripSession(User user, BudgetLevel budgetRange, TransportType transportation,
                        Double accommodationLatitude, Double accommodationLongitude) {
        this.user = user;
        this.budgetRange = budgetRange;
        this.transportation = transportation;
        this.accommodationLatitude = accommodationLatitude;
        this.accommodationLongitude = accommodationLongitude;
        // 반경을 인자로 받지 않는 이유: 이동수단이 정해지면 반경도 정해진다.
        // 따로 받으면 "walk인데 반경 20km" 같은 어긋난 세션이 만들어질 수 있다.
        this.searchRadiusKm = transportation.getSearchRadiusKm();
    }

    public static TripSession create(User user, BudgetLevel budgetRange, TransportType transportation,
                                     Double accommodationLatitude, Double accommodationLongitude) {
        return new TripSession(user, budgetRange, transportation,
                accommodationLatitude, accommodationLongitude);
    }

    /**
     * 숙소 좌표를 지운다. 회원 탈퇴 때만 부른다. (roadmap 9-5, 결정 20)
     *
     * 세션 행은 남긴다 — 예산·이동수단·반경은 사람을 가리키지 못하는 이용기록이다.
     * `0, 0`으로 덮지 않는 이유: 그것도 실제 좌표(기니만)라 파기된 값인지 구분할 수 없다.
     */
    public void eraseAccommodationLocation() {
        this.accommodationLatitude = null;
        this.accommodationLongitude = null;
    }
}
