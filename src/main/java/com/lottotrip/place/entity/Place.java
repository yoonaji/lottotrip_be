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
 * <p><b>강원 전역을 배치로 미리 적재한다.</b> (roadmap 결정 10 — 배치 적재 + 세부조회 실시간)
 * 슬롯 추첨은 이 테이블만 보고 하며, TourAPI를 실시간으로 부르지 않는다.
 * 공공 API 실시간 호출은 {@code GET /slot/results/&#123;slotId&#125;}(세부조회)가 담당한다.
 *
 * <p>⚠️ 한때 <b>결정 8(온디맨드 실시간 조회)</b>로 "미리 적재하지 않고 뽑힌 1건만 저장"하는 설계였다.
 * 2026-08-13 회의에서 뒤집혔다. 예산·무장애 정보가 장소 코드 단위로만 조회돼,
 * 온디맨드로는 "뽑고 나서 조건 확인 → 미달이면 재추첨"이라는 루프를 피할 수 없었기 때문이다.
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

    /**
     * 소속 시·군. <b>배치가 채운다.</b> (roadmap 5-8)
     *
     * <p>TourAPI는 지역을 코드({@code areacode}·{@code sigungucode})로만 준다.
     * {@code states}·{@code cities}에 그 코드를 담을 컬럼을 추가하고 미리 시드해 두면,
     * 적재할 때 코드로 조회해 이을 수 있다.
     *
     * <p><b>그래도 nullable인 이유:</b> 지역코드 시드가 아직 없는 상태에서도 장소 적재는 굴러가야 한다.
     * 여기에 NOT NULL을 걸면 시드가 하루 늦어질 때 적재 전체가 멈춘다. NULL은 "아직 잇지 않음"을 뜻한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
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

    /**
     * 예산 등급. <b>지금은 채우지 않는다.</b> (roadmap 결정 9)
     *
     * <p>TourAPI가 요금을 부분적으로만 준다. 우리가 여행지로 쓰는 <b>관광지(12)에는 요금 필드가
     * 스키마에 아예 없고</b>, 있는 것도 {@code "※ 공연 및 행사 별 상이"} 같은 서술형이라 등급을 매길 수 없다.
     * 핵심 기능(뽑기 → 미션)을 먼저 완성하기로 했다.
     *
     * <p><b>기본값을 박지 않고 NULL로 두는 이유:</b> {@code MEDIUM} 같은 값을 넣어 두면
     * 그것이 실제로 계산한 값인지 "아직 안 함" 표시인지 구분할 수 없다. 나중에 등급을 채우기로 하면
     * 어느 행을 계산해야 하는지 찾지 못하게 된다. NULL이면 "NULL인 것만 채운다"가 그대로 성립한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "budget_tier", length = 20)
    private BudgetLevel budgetTier;

    /**
     * 뚜벅이 모드 가중치. <b>사용하지 않는다.</b> (roadmap 결정 9)
     *
     * <p>추첨이 이미 반경으로 거리를 제한하므로("걸어갈 수 있는가"는 반경이 담당한다)
     * 별도 접근성 점수는 중복이다. TourAPI가 주지 않는 우리 값이기도 하다.
     */
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
