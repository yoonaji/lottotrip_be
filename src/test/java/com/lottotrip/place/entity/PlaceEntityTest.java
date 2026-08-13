package com.lottotrip.place.entity;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.enums.MediaType;
import com.lottotrip.support.PostgresContainerSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code places} / {@code place_media} 테이블 매핑 검증. (tour_api_erd.md 1 — places / place_media)
 *
 * <p>좌표는 ERD의 {@code POINT} 대신 위도·경도 컬럼 두 개로 나눠 저장하기로 했다.
 * 반경 검색에 PostGIS를 도입하지 않고, 사각형 범위 필터 + 거리 계산으로 처리하기 위함이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlaceEntityTest extends PostgresContainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    private City persistedCity() {
        State state = State.create("강원특별자치도");
        entityManager.persist(state);
        City city = City.create(state, "강릉시");
        entityManager.persist(city);
        return city;
    }

    /**
     * 인자를 이름으로 넣는다. 위치로 넘기면 {@code name}·{@code description}·{@code address}·
     * {@code contentId}·{@code contentTypeId}가 전부 String이라 순서를 바꿔도 컴파일이 통과한다.
     */
    private Place.PlaceBuilder samplePlace(City city) {
        return Place.builder()
                .contentId("3535323")
                .contentTypeId("12")
                .city(city)
                .name("사천진해변")
                .description("조용한 해변")
                .category(TravelCategory.BEACH)
                .address("강원 강릉시 사천면")
                .latitude(37.8021)
                .longitude(128.8954)
                .budgetTier(BudgetLevel.LOW)
                .publicTransportWeight(3)
                .modifiedTime(LocalDateTime.of(2026, 3, 1, 12, 0));
    }

    @Test
    @DisplayName("장소를 저장하면 placeId와 createdAt이 자동으로 채워진다")
    void assignsIdAndCreatedAt() {
        Place place = samplePlace(persistedCity()).build();

        entityManager.persist(place);
        entityManager.flush();

        assertThat(place.getId()).isNotNull();
        assertThat(place.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("위도·경도가 소수점까지 그대로 저장되고 다시 조회된다")
    void persistsCoordinate() {
        Place place = samplePlace(persistedCity()).build();
        entityManager.persist(place);
        entityManager.flush();
        entityManager.clear();

        // 반경 검색과 distanceKm 계산이 이 값에 달려 있다. 소수점이 잘리면 위치가 통째로 어긋난다.
        Place found = entityManager.find(Place.class, place.getId());
        assertThat(found.getLatitude()).isEqualTo(37.8021);
        assertThat(found.getLongitude()).isEqualTo(128.8954);
    }

    @Test
    @DisplayName("장소는 자신이 속한 시·군을 함께 조회할 수 있다")
    void placeBelongsToCity() {
        Place place = samplePlace(persistedCity()).build();
        entityManager.persist(place);
        entityManager.flush();
        entityManager.clear();

        Place found = entityManager.find(Place.class, place.getId());
        assertThat(found.getCity().getCityName()).isEqualTo("강릉시");
    }

    @Test
    @DisplayName("category와 budgetTier는 숫자가 아니라 이름 문자열로 저장된다")
    void storesEnumsAsName() {
        Place place = samplePlace(persistedCity()).build();
        entityManager.persist(place);
        entityManager.flush();

        Object[] stored = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("SELECT category, budget_tier FROM places WHERE place_id = :id")
                .setParameter("id", place.getId())
                .getSingleResult();

        assertThat(stored[0]).hasToString("BEACH");
        assertThat(stored[1]).hasToString("LOW");
    }

    @Test
    @DisplayName("category는 응답에 내려줄 한글 표시명을 갖는다")
    void categoryHasDisplayName() {
        // 명세 응답 예시: "category": "해변" (tour_api_erd.md 4-3)
        assertThat(TravelCategory.BEACH.getDisplayName()).isEqualTo("해변");
    }

    // ---------- 미사용 컬럼 비우기 (5-4, 결정 9 · 결정 10) ----------

    @Test
    @DisplayName("시·군과 예산 등급 없이도 장소를 저장할 수 있다")
    void persistsWithoutCityAndBudgetTier() {
        // 배치(결정 10)가 적재하는 것은 TourAPI가 주는 값뿐이다.
        //   - budget_tier : TourAPI가 관광지(12)에는 요금을 주지 않는다. 결정 9로 계산하지 않기로 했다
        //   - city_id     : 지역코드 시드(5-8)가 아직 없어도 적재는 굴러가야 한다
        // 둘 다 NOT NULL로 두면 배치가 장소를 넣는 순간 통째로 멈춘다.
        Place place = Place.builder()
                .contentId("3535323")
                .name("사천진해변")
                .category(TravelCategory.BEACH)
                .address("강원 강릉시 사천면")
                .latitude(37.8021)
                .longitude(128.8954)
                .build();

        entityManager.persist(place);
        entityManager.flush();
        entityManager.clear();

        Place found = entityManager.find(Place.class, place.getId());
        assertThat(found.getId()).isNotNull();
        assertThat(found.getCity()).isNull();
        assertThat(found.getBudgetTier()).isNull();
        assertThat(found.getPublicTransportWeight()).isNull();
        // 없어도 되는 것과 반드시 있어야 하는 것을 함께 고정한다.
        assertThat(found.getName()).isEqualTo("사천진해변");
        assertThat(found.getLatitude()).isEqualTo(37.8021);
        assertThat(found.getLongitude()).isEqualTo(128.8954);
    }

    @Test
    @DisplayName("이름·좌표·분류는 여전히 필수다 — 비면 추첨도 응답도 성립하지 않는다")
    void stillRequiresNameCoordinateAndCategory() {
        // nullable로 푼 것은 "지금 안 쓰는 값"뿐이다. 응답에 나가는 값까지 풀면
        // 빈 장소가 저장돼 슬롯 결과가 비어 나간다.
        Place noCoordinate = Place.builder()
                .contentId("3535323")
                .name("좌표없음")
                .category(TravelCategory.BEACH)
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(noCoordinate);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    // ---------- TourAPI 식별 컬럼 (5-7, 결정 10) ----------

    @Test
    @DisplayName("TourAPI 장소 코드·타입 코드·수정일시가 그대로 저장되고 다시 조회된다")
    void persistsTourApiIdentifiers() {
        // content_id는 세부조회(GET /slot/results/{slotId})가 detailCommon2를 부를 때 쓰는 키다.
        // 이 값이 없으면 적재해 둔 장소를 TourAPI에서 다시 찾을 방법이 없다.
        Place place = samplePlace(persistedCity()).build();

        entityManager.persist(place);
        entityManager.flush();
        entityManager.clear();

        Place found = entityManager.find(Place.class, place.getId());
        assertThat(found.getContentId()).isEqualTo("3535323");
        assertThat(found.getContentTypeId()).isEqualTo("12");
        assertThat(found.getModifiedTime()).isEqualTo(LocalDateTime.of(2026, 3, 1, 12, 0));
        // 우리 PK와 TourAPI 코드는 별개의 값이다. 둘 다 있어야 한다.
        assertThat(found.getId()).isNotNull();
        assertThat(found.getId()).isNotEqualTo(found.getContentId());
    }

    @Test
    @DisplayName("장소 코드가 없으면 저장할 수 없다 — 세부조회를 영영 못 하는 행이 된다")
    void rejectsPlaceWithoutContentId() {
        Place noContentId = samplePlace(persistedCity())
                .contentId(null)
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(noContentId);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("같은 장소 코드를 두 번 저장하면 거절된다 — 배치를 다시 돌려도 중복되지 않는다")
    void rejectsDuplicateContentId() {
        // 배치는 여러 번 돌린다(초기 적재 실패 후 재시도, 정기 갱신).
        // 제약이 없으면 같은 장소가 두 행으로 쌓이고, 추첨에서 두 배 확률로 뽑힌다.
        City city = persistedCity();
        entityManager.persist(samplePlace(city).build());
        entityManager.flush();

        Place duplicate = samplePlace(city)
                .name("이름이 달라도 같은 장소다")
                .build();

        // 제약 이름까지 확인한다. Exception만 보면 다른 이유로 터져도 통과해버려서
        // "UNIQUE가 실제로 걸렸다"를 증명하지 못한다.
        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).hasMessageContaining("uk_places_content_id");
    }

    @Test
    @DisplayName("타입 코드와 수정일시는 비어 있어도 저장된다")
    void persistsWithoutContentTypeIdAndModifiedTime() {
        // 둘 다 TourAPI가 주는 값이라 사실상 항상 채워지지만, 응답에 나가지 않고
        // 비어도 슬롯이 성립하므로 제약으로 막지 않는다.
        Place place = samplePlace(persistedCity())
                .contentTypeId(null)
                .modifiedTime(null)
                .build();

        entityManager.persist(place);
        entityManager.flush();
        entityManager.clear();

        Place found = entityManager.find(Place.class, place.getId());
        assertThat(found.getContentTypeId()).isNull();
        assertThat(found.getModifiedTime()).isNull();
        assertThat(found.getContentId()).isEqualTo("3535323");
    }

    @Test
    @DisplayName("장소 이미지를 저장하면 해당 장소를 함께 조회할 수 있다")
    void mediaBelongsToPlace() {
        Place place = samplePlace(persistedCity()).build();
        entityManager.persist(place);
        PlaceMedia media = PlaceMedia.create(place, "https://cdn.example.com/beach.jpg", MediaType.IMAGE);

        entityManager.persist(media);
        entityManager.flush();
        entityManager.clear();

        PlaceMedia found = entityManager.find(PlaceMedia.class, media.getId());
        assertThat(found.getMediaUrl()).isEqualTo("https://cdn.example.com/beach.jpg");
        assertThat(found.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(found.getPlace().getName()).isEqualTo("사천진해변");
    }

    @Test
    @DisplayName("한 장소에 여러 장의 이미지를 붙일 수 있다")
    void placeHasManyMedia() {
        Place place = samplePlace(persistedCity()).build();
        entityManager.persist(place);

        entityManager.persist(PlaceMedia.create(place, "https://cdn.example.com/1.jpg", MediaType.IMAGE));
        entityManager.persist(PlaceMedia.create(place, "https://cdn.example.com/2.jpg", MediaType.IMAGE));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }
}
