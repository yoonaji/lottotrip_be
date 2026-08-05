package com.lottotrip.place.entity;

import com.lottotrip.common.enums.BudgetLevel;
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
 * 장소 마스터. (tour_api_erd.md 1 — places)
 *
 * <p>TourAPI에서 배치로 적재하는 데이터이며, 슬롯 추첨의 후보가 되는 테이블이다.
 */
@Entity
@Table(
        name = "places",
        // 반경 검색은 "위도·경도 사각형 범위로 후보를 좁힌 뒤 정확한 거리를 계산"하는 방식이다.
        // 1차 필터가 인덱스를 타지 못하면 장소가 늘어날수록 전체를 훑게 된다.
        indexes = @Index(name = "idx_places_coordinate", columnList = "latitude, longitude")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TravelCategory category;

    @Column(length = 255)
    private String address;

    /**
     * ERD는 좌표를 {@code POINT} 하나로 적어 두었지만 위도·경도 두 컬럼으로 나눠 저장한다.
     *
     * <p>PostGIS를 도입하면 테스트·로컬·배포 환경을 모두 바꿔야 하는데, 이 서비스의 반경 검색은
     * 사각형 범위 필터 + 거리 계산으로 충분하다. 응답도 위도·경도를 각각 요구한다.
     */
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_tier", nullable = false, length = 20)
    private BudgetLevel budgetTier;

    /** 뚜벅이 모드 가중치. ⚠️ 값의 범위·방향은 아직 확인되지 않았다. (tour_api_erd.md 미확정 항목) */
    @Column(name = "public_transport_weight")
    private Integer publicTransportWeight;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private Place(City city, String name, String description, TravelCategory category, String address,
                  Double latitude, Double longitude, BudgetLevel budgetTier, Integer publicTransportWeight) {
        this.city = city;
        this.name = name;
        this.description = description;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.budgetTier = budgetTier;
        this.publicTransportWeight = publicTransportWeight;
    }

    public static Place create(City city, String name, String description, TravelCategory category, String address,
                               Double latitude, Double longitude, BudgetLevel budgetTier,
                               Integer publicTransportWeight) {
        return new Place(city, name, description, category, address,
                latitude, longitude, budgetTier, publicTransportWeight);
    }
}
