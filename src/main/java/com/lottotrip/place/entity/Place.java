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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 장소 마스터. (tour_api_erd.md 1 — places)
 *
 * **이 테이블은 미리 채워 두지 않는다.** `draw`가 TourAPI를 실시간으로 부르고,
 * 뽑힌 1건만 {@link com.lottotrip.place.service.PlaceUpserter}가 담는다(결정 12).
 * 그래서 여기 쌓이는 것은 전국 목록이 아니라 **우리 서비스에서 한 번이라도 뽑힌 장소**다.
 *
 * **그래도 DB에 담는 이유:** `saved_slots`·`course_items`·`missions`가 `place_id`를 FK로 가리킨다.
 * 응답에 실어 보내고 끝내면 사용자의 코스·미션이 참조할 대상이 없다.
 *
 * 설계가 두 번 뒤집힌 자리다. 결정 8(온디맨드) → 결정 10(강원 전역 배치 적재, 2026-08-13)
 * → **결정 12(온디맨드 복귀, 2026-08-15)**. 결정 10의 근거는 "예산·무장애를 뽑은 뒤 확인하면
 * 재추첨 루프가 생긴다"였는데, 예산을 필터로 쓰지 않기로 하면서(결정 9) 근거가 사라졌다.
 */
@Entity
@Table(
        name = "places",
        // 과거 배치 방식(결정 10)의 잔재. 현재 사용 안 함.
        // 그때는 반경 검색을 DB에서 했다 — 사각형 범위로 후보를 좁히고(이 인덱스) Haversine으로 걸러냈다.
        // 결정 12로 반경 검색이 TourAPI로 넘어가 이 인덱스를 타는 조회가 없다.
        // 남겨 둔 이유: 인덱스 하나의 비용은 작고, DB 조회로 되돌리면 다시 필요해진다.
        indexes = @Index(name = "idx_places_coordinate", columnList = "latitude, longitude"),
        uniqueConstraints = @UniqueConstraint(name = "uk_places_content_id", columnNames = "content_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    /**
     * 우리 DB가 채번하는 식별자. `saved_slots`·`course_items`·`missions`가 이 값을 참조하고,
     * API 응답의 `placeId`로 나간다.
     *
     * ⚠️ {@link #contentId}와 **다른 값이다.** 이쪽은 우리가, 저쪽은 TourAPI가 부여한다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    /**
     * TourAPI 장소 코드(`contentid`). **세부조회의 유일한 호출 키다.** (roadmap 5-7)
     *
     * `GET /slot/results/{slotId}`가 이 값으로 `detailCommon2`를 불러
     * 설명·홈페이지를 실시간으로 받아 온다. 저장해 두지 않으면 **담아 둔 장소를 TourAPI에서 다시 찾을 방법이 없다.**
     * 장소명으로 재검색하는 방법은 동명 장소를 구분하지 못하고 호출도 2배가 된다.
     *
     * **UNIQUE인 이유:** 랜덤 추첨이라 같은 장소가 여러 번 뽑히고, 그때마다
     * {@link com.lottotrip.place.service.PlaceUpserter}가 **이 컬럼으로 기존 행을 찾는다.**
     * 제약이 없으면 같은 장소가 두 행으로 쌓여, 코스·미션이 어느 쪽을 가리켰는지에 따라 결과가 갈린다.
     * 조회 후 저장만으로는 같은 장소로 두 요청이 동시에 올 때 둘 다 통과하므로 DB 차원에서 막는다.
     */
    @Column(name = "content_id", nullable = false, length = 20)
    private String contentId;

    /**
     * TourAPI 관광타입 코드(`contenttypeid`). 12=관광지, 14=문화시설, 39=음식점 …
     *
     * `detailIntro2`는 {@link #contentId}만으로는 부족하고 이 값을 **함께** 요구한다.
     * {@link #category}를 정한 근거이기도 해서, 나중에 매핑 규칙을 바꿀 때 다시 계산할 수 있다.
     *
     * 응답에 나가지 않고 비어도 슬롯이 성립하므로 nullable로 둔다.
     */
    @Column(name = "content_type_id", length = 10)
    private String contentTypeId;

    /**
     * 소속 시·군. **`PlaceUpserter.resolveCity()`가 채운다.** (roadmap 5-8)
     *
     * TourAPI는 지역을 코드(`areacode`·`sigungucode`)로만 준다.
     * `states`·`cities`에 그 코드를 담을 컬럼을 두고 `RegionSeeder`로 미리 시드해 두면,
     * 장소를 담을 때 코드로 조회해 이을 수 있다.
     *
     * **그래도 nullable인 이유:** 잇지 못하는 경우가 실제로 있다. 시드가 아직 없을 때,
     * 그리고 결정 12로 **서비스 범위가 전국이 되어 시드에 없는 지역이 뽑힐 때**다.
     * NOT NULL을 걸면 그때마다 `draw`가 통째로 실패한다. NULL은 "아직 잇지 않음"을 뜻한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 장소 분류. TourAPI `cat2`를 그대로 옮긴 값이다. (roadmap 6-15, 결정 16)
     *
     * ## ⚠️ 이 컬럼에는 CHECK 제약이 자동으로 붙는다 (결정 16)
     * Hibernate는 `@Enumerated` 컬럼에 "이 값들만 허용"이라는 CHECK 제약을 만든다.
     * 그런데 `ddl-auto: update`는 **이미 만들어진 제약을 고치지 않아서**, enum에 값을 더하면
     * 그 값이 처음 저장되는 순간 실패한다(2026-08-15에 실제로 겪었다).
     *
     * **제약 생성을 막을 방법이 없다**(Hibernate 6.6). `columnDefinition` ·
     * `@JdbcTypeCode(VARCHAR)` · `AttributeConverter`를 모두 시도했지만 전부 생성된다.
     * 끄는 설정도 없다.
     *
     * **그래서 운영 절차로 푼다.** 제약은 **테이블을 만들 때만** 생기고
     * `update`가 다시 붙이지는 않으므로, **기존 DB에서 한 번 지우면 그 DB에서는 다시 안 생긴다.**
     * 새로 만드는 DB는 그 시점 enum으로 정확히 생성되므로 애초에 어긋나지 않는다.
     * 근본 해결은 마이그레이션 도구(Flyway) 도입이다 — 배포 전에 정하기로 했다.
     *
     * 길이를 30으로 둔 이유: 가장 긴 상수명이 `NATURE_ATTRACTION`(17자)이고
     * 분류가 더 늘 수 있어 여유를 뒀다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TravelCategory category;

    @Column(length = 255)
    private String address;

    /**
     * ERD는 좌표를 `POINT` 하나로 적어 두었지만 위도·경도 두 컬럼으로 나눠 저장한다.
     *
     * PostGIS를 도입하면 테스트·로컬·배포 환경을 모두 바꿔야 하는데, 이 서비스의 반경 검색은
     * 사각형 범위 필터 + 거리 계산으로 충분하다. 응답도 위도·경도를 각각 요구한다.
     */
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /**
     * 예산 등급. **지금은 채우지 않는다.** (roadmap 결정 9)
     *
     * TourAPI가 요금을 부분적으로만 준다. 우리가 여행지로 쓰는 **관광지(12)에는 요금 필드가
     * 스키마에 아예 없고**, 있는 것도 `"※ 공연 및 행사 별 상이"` 같은 서술형이라 등급을 매길 수 없다.
     * 핵심 기능(뽑기 → 미션)을 먼저 완성하기로 했다.
     *
     * **기본값을 박지 않고 NULL로 두는 이유:** `MEDIUM` 같은 값을 넣어 두면
     * 그것이 실제로 계산한 값인지 "아직 안 함" 표시인지 구분할 수 없다. 나중에 등급을 채우기로 하면
     * 어느 행을 계산해야 하는지 찾지 못하게 된다. NULL이면 "NULL인 것만 채운다"가 그대로 성립한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "budget_tier", length = 20)
    private BudgetLevel budgetTier;

    /**
     * 뚜벅이 모드 가중치. **사용하지 않는다.** (roadmap 결정 9)
     *
     * 추첨이 이미 반경으로 거리를 제한하므로("걸어갈 수 있는가"는 반경이 담당한다)
     * 별도 접근성 점수는 중복이다. TourAPI가 주지 않는 우리 값이기도 하다.
     */
    @Column(name = "public_transport_weight")
    private Integer publicTransportWeight;

    /**
     * TourAPI가 알려주는 **장소 정보의 최종 수정일시**(`modifiedtime`).
     *
     * ⚠️ **원래 용도(정기 갱신 배치의 비교 기준)는 결정 12로 없어졌다.** 배치가 폐기됐고,
     * 지금은 `PlaceUpserter`가 같은 장소를 다시 만날 때마다 TourAPI 값으로 덮어쓸 뿐이다.
     *
     * **그래도 계속 담는다.** 나중에 컬럼을 추가하면 이미 담긴 행은 값이 비어 비교할 대상이 없고,
     * 결국 전체를 다시 받아야 한다. 지금 담아 두는 비용은 컬럼 하나다.
     *
     * ⚠️ {@link #createdAt}과 다른 값이다. 이쪽은 **TourAPI 쪽에서** 장소 정보가 바뀐 시각이고,
     * `createdAt`은 **우리 DB에** 행이 들어온 시각이다.
     */
    @Column(name = "modified_time")
    private LocalDateTime modifiedTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 인자를 위치가 아니라 **이름으로** 받는다.
     *
     * 필드가 11개인데 그중 `name`·`description`·`address`·`contentId`·
     * `contentTypeId` 다섯이 전부 `String`이다. 위치로 받으면 순서를 바꿔 넣어도
     * 컴파일이 통과하고, 하필 `contentId`는 세부조회의 호출 키라 잘못 들어가면
     * 그 장소는 영영 조회되지 않는다. 이름을 붙이면 이 실수가 원천적으로 불가능하다.
     */
    /**
     * TourAPI에서 다시 받아온 값으로 갱신한다. `PlaceUpserter`가 **이미 담은 장소를 만났을 때** 쓴다.
     *
     * 새로 만든 `Place`를 통째로 받는 이유는 인자를 하나씩 나열하면 다시 12개가 되기 때문이다.
     * `PlaceUpserter`는 어차피 응답 항목으로 `Place`를 한 번 조립하므로, 그걸 그대로 넘기면 된다.
     *
     * **`contentId`는 바꾸지 않는다.** 이 행이 어느 장소인지 정하는 값이라
     * 바뀌면 다른 장소가 되어 버린다. `createdAt`도 우리 DB에 처음 담긴 시각이라 그대로 둔다.
     */
    public void updateFrom(Place source) {
        this.contentTypeId = source.contentTypeId;
        this.city = source.city;
        this.name = source.name;
        this.description = source.description;
        this.category = source.category;
        this.address = source.address;
        this.latitude = source.latitude;
        this.longitude = source.longitude;
        this.modifiedTime = source.modifiedTime;
    }

    @Builder
    private Place(String contentId, String contentTypeId, City city, String name, String description,
                  TravelCategory category, String address, Double latitude, Double longitude,
                  BudgetLevel budgetTier, Integer publicTransportWeight, LocalDateTime modifiedTime) {
        this.contentId = contentId;
        this.contentTypeId = contentTypeId;
        this.city = city;
        this.name = name;
        this.description = description;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.budgetTier = budgetTier;
        this.publicTransportWeight = publicTransportWeight;
        this.modifiedTime = modifiedTime;
    }
}
