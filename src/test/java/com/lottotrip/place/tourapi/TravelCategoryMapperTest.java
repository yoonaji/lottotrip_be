package com.lottotrip.place.tourapi;

import com.lottotrip.place.entity.TravelCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TourAPI 분류 코드 → 우리 {@link TravelCategory} 매핑 검증. (roadmap 5-9)
 *
 * <p><b>1:1이 아니다.</b> TourAPI의 관광타입은 6종인데 우리 분류는 8종이고,
 * 특히 <b>관광지(12) 하나에 자연·해변·역사가 전부 들어 있다.</b>
 * 그래서 관광타입만으로는 못 나누고 분류코드({@code cat1}~{@code cat3})를 함께 본다.
 *
 * <p>TourAPI 대분류: {@code A01}=자연 · {@code A02}=인문(문화·역사) · {@code A03}=레포츠 ·
 * {@code A04}=쇼핑 · {@code A05}=음식
 */
class TravelCategoryMapperTest {

    private final TravelCategoryMapper mapper = new TravelCategoryMapper();

    // ---------- 관광타입만으로 정해지는 것 ----------

    @Test
    @DisplayName("문화시설·레포츠·쇼핑은 관광타입만으로 정해진다")
    void mapsByContentTypeAlone() {
        assertThat(mapper.map("14", "A02", null, null)).isEqualTo(TravelCategory.CULTURE);
        assertThat(mapper.map("28", "A03", null, null)).isEqualTo(TravelCategory.LEISURE);
        assertThat(mapper.map("38", "A04", null, null)).isEqualTo(TravelCategory.SHOPPING);
    }

    // ---------- 관광지(12)를 분류코드로 나누기 ----------

    @Test
    @DisplayName("관광지 중 자연은 NATURE다")
    void mapsNaturalTouristSpotToNature() {
        // cat1 = A01(자연)
        assertThat(mapper.map("12", "A01", "A0102", "A01021000")).isEqualTo(TravelCategory.NATURE);
    }

    @Test
    @DisplayName("관광지 중 해수욕장은 BEACH다 — 자연이지만 따로 뽑는다")
    void mapsBeachToBeach() {
        // 슬롯 응답에 "해변"으로 나가는 값이다. 자연으로 뭉뚱그리면 사용자가 받는 결과가 밋밋해진다.
        assertThat(mapper.map("12", "A01", "A0101", "A01011200")).isEqualTo(TravelCategory.BEACH);
    }

    @Test
    @DisplayName("관광지 중 역사관광지는 HISTORY다")
    void mapsHistoricSiteToHistory() {
        // cat2 = A0201(역사관광지)
        assertThat(mapper.map("12", "A02", "A0201", "A02010100")).isEqualTo(TravelCategory.HISTORY);
    }

    @Test
    @DisplayName("관광지 중 역사가 아닌 인문은 CULTURE다")
    void mapsOtherHumanitiesToCulture() {
        // cat2 = A0203(체험관광지) 등
        assertThat(mapper.map("12", "A02", "A0203", "A02030400")).isEqualTo(TravelCategory.CULTURE);
    }

    // ---------- 음식점(39)을 분류코드로 나누기 ----------

    @Test
    @DisplayName("음식점은 FOOD, 그중 카페는 CAFE다")
    void mapsCafeApartFromFood() {
        assertThat(mapper.map("39", "A05", "A0502", "A05020100")).isEqualTo(TravelCategory.FOOD);
        assertThat(mapper.map("39", "A05", "A0502", "A05020900")).isEqualTo(TravelCategory.CAFE);
    }

    // ---------- 모르는 값이 와도 배치가 멈추면 안 된다 ----------

    @Test
    @DisplayName("모르는 관광타입이 와도 예외 없이 값을 준다")
    void neverReturnsNullForUnknownType() {
        // category는 NOT NULL이다. 여기서 null이나 예외가 나오면 그 장소 하나 때문에
        // 배치 전체가 멈춘다. 수천 건을 훑는 작업에서 가장 나쁜 실패 방식이다.
        assertThat(mapper.map("99", null, null, null)).isNotNull();
        assertThat(mapper.map(null, null, null, null)).isNotNull();
        assertThat(mapper.map("12", null, null, null)).isNotNull();
    }

    @Test
    @DisplayName("분류코드 대소문자가 달라도 같게 읽는다")
    void isCaseInsensitive() {
        assertThat(mapper.map("12", "a01", "a0101", "a01011200")).isEqualTo(TravelCategory.BEACH);
    }

    // ---------- 전 종류 매핑 (roadmap 6-10, 결정 13) ----------

    @Test
    @DisplayName("숙박은 LODGING이다 — 예전에는 NATURE로 떨어져 모텔이 '자연'으로 저장됐다")
    void mapsLodging() {
        // 결정 13으로 전 종류를 담게 되면서 숙박(32)이 실제로 들어온다.
        // 대응 값이 없으면 기본값 NATURE로 떨어지는데, 그러면 응답에 "에쿠스모텔 · 자연"이 나간다.
        assertThat(mapper.map("32", "B02", "B0201", "B02010900")).isEqualTo(TravelCategory.LODGING);
    }

    @Test
    @DisplayName("축제·공연·행사는 FESTIVAL이다")
    void mapsFestival() {
        assertThat(mapper.map("15", "A02", "A0207", "A02070100")).isEqualTo(TravelCategory.FESTIVAL);
    }

    @Test
    @DisplayName("여행코스는 COURSE다")
    void mapsCourse() {
        assertThat(mapper.map("25", "C01", "C0112", "C01120001")).isEqualTo(TravelCategory.COURSE);
    }

    @Test
    @DisplayName("강원에 실제로 있는 8개 관광타입이 전부 고유한 분류로 간다 — 기본값으로 뭉치지 않는다")
    void mapsEveryContentTypeDistinctly() {
        // 실측(2026-08-15, areaCode=32)에 존재하는 종류 전부다.
        // 하나라도 기본값으로 떨어지면 그 종류는 응답에서 정체를 알 수 없게 된다.
        assertThat(mapper.map("12", "A01", null, null)).isEqualTo(TravelCategory.NATURE);
        assertThat(mapper.map("14", null, null, null)).isEqualTo(TravelCategory.CULTURE);
        assertThat(mapper.map("15", null, null, null)).isEqualTo(TravelCategory.FESTIVAL);
        assertThat(mapper.map("25", null, null, null)).isEqualTo(TravelCategory.COURSE);
        assertThat(mapper.map("28", null, null, null)).isEqualTo(TravelCategory.LEISURE);
        assertThat(mapper.map("32", null, null, null)).isEqualTo(TravelCategory.LODGING);
        assertThat(mapper.map("38", null, null, null)).isEqualTo(TravelCategory.SHOPPING);
        assertThat(mapper.map("39", null, null, null)).isEqualTo(TravelCategory.FOOD);
    }

    @Test
    @DisplayName("모든 분류에 사람이 읽을 이름이 있다 — 응답의 category로 그대로 나간다")
    void everyCategoryHasDisplayName() {
        for (TravelCategory category : TravelCategory.values()) {
            assertThat(category.getDisplayName())
                    .as("%s의 displayName", category)
                    .isNotBlank();
        }
    }
}
