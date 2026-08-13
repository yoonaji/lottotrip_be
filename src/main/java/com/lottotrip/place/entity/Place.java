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
        indexes = @Index(name = "idx_places_coordinate", columnList = "latitude, longitude"),
        uniqueConstraints = @UniqueConstraint(name = "uk_places_content_id", columnNames = "content_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    /**
     * 우리 DB가 채번하는 식별자. {@code saved_slots}·{@code course_items}·{@code missions}가 이 값을 참조하고,
     * API 응답의 {@code placeId}로 나간다.
     *
     * <p>⚠️ {@link #contentId}와 <b>다른 값이다.</b> 이쪽은 우리가, 저쪽은 TourAPI가 부여한다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    /**
     * TourAPI 장소 코드({@code contentid}). <b>세부조회의 유일한 호출 키다.</b> (roadmap 5-7, 결정 10)
     *
     * <p>{@code GET /slot/results/&#123;slotId&#125;}가 이 값으로 {@code detailCommon2}를 불러
     * 설명·이미지를 실시간으로 받아 온다. 저장해 두지 않으면 <b>적재한 장소를 TourAPI에서 다시 찾을 방법이 없다.</b>
     * 장소명으로 재검색하는 방법은 동명 장소를 구분하지 못하고 호출도 2배가 된다.
     *
     * <p><b>UNIQUE인 이유:</b> 배치는 여러 번 돈다(초기 적재 실패 후 재시도, 정기 갱신).
     * 제약이 없으면 같은 장소가 두 행으로 쌓이고, <b>추첨에서 그 장소가 두 배 확률로 뽑힌다.</b>
     * 조회 후 저장만으로는 배치를 두 개 동시에 돌릴 때 둘 다 통과하므로 DB 차원에서 막는다.
     */
    @Column(name = "content_id", nullable = false, length = 20)
    private String contentId;

    /**
     * TourAPI 관광타입 코드({@code contenttypeid}). 12=관광지, 14=문화시설, 39=음식점 …
     *
     * <p>{@code detailIntro2}는 {@link #contentId}만으로는 부족하고 이 값을 <b>함께</b> 요구한다.
     * {@link #category}를 정한 근거이기도 해서, 나중에 매핑 규칙을 바꿀 때 다시 계산할 수 있다.
     *
     * <p>응답에 나가지 않고 비어도 슬롯이 성립하므로 nullable로 둔다.
     */
    @Column(name = "content_type_id", length = 10)
    private String contentTypeId;

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

    /**
     * TourAPI가 알려주는 <b>장소 정보의 최종 수정일시</b>({@code modifiedtime}).
     *
     * <p>정기 갱신 배치가 "무엇을 다시 받을지" 고르는 기준이다. 목록 조회는 싸고(14회) 상세 조회가
     * 비싸므로(1,241회), 목록만 훑어 이 값을 비교하고 <b>바뀐 것만</b> 상세를 다시 받는다.
     *
     * <p><b>아직 쓰지 않지만 첫 적재부터 담는다.</b> 나중에 컬럼을 추가하면 이미 적재된 행은 값이 비어
     * 비교할 대상이 없고, 결국 전체를 다시 받아야 한다. 지금 담아 두는 비용은 컬럼 하나다.
     *
     * <p>⚠️ {@link #createdAt}과 다른 값이다. 이쪽은 <b>TourAPI 쪽에서</b> 장소 정보가 바뀐 시각이고,
     * {@code createdAt}은 <b>우리 DB에</b> 행이 들어온 시각이다.
     */
    @Column(name = "modified_time")
    private LocalDateTime modifiedTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 인자를 위치가 아니라 <b>이름으로</b> 받는다.
     *
     * <p>필드가 11개인데 그중 {@code name}·{@code description}·{@code address}·{@code contentId}·
     * {@code contentTypeId} 다섯이 전부 {@code String}이다. 위치로 받으면 순서를 바꿔 넣어도
     * 컴파일이 통과하고, 하필 {@code contentId}는 세부조회의 호출 키라 잘못 들어가면
     * 그 장소는 영영 조회되지 않는다. 이름을 붙이면 이 실수가 원천적으로 불가능하다.
     */
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
