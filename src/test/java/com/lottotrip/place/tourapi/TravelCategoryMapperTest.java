package com.lottotrip.place.tourapi;

import com.lottotrip.place.entity.TravelCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TourAPI 분류 코드 → 우리 분류 매핑 검증. (roadmap 6-15, 결정 16)
 *
 * <p><b>⚠️ 6-15에서 기준이 통째로 바뀌었다.</b> 예전에는 관광타입({@code contenttypeid})과
 * {@code cat1}~{@code cat3}를 섞어 11종으로 나눴는데, 지금은 <b>{@code cat2} 하나만</b> 본다.
 *
 * <p><b>왜 바꿨나.</b> 여행 스타일 6종(휴식·맛집·감상·체험·활동·탐험)으로 후보를 거르려는데
 * 기존 분류로는 <b>체험·활동·탐험이 전부 {@code LEISURE} 하나로 뭉쳤다.</b>
 * {@code cat2}에는 "휴양관광지"·"체험관광지"가 이름 그대로 있어 그대로 쓸 수 있다.
 */
class TravelCategoryMapperTest {

    private final TravelCategoryMapper mapper = new TravelCategoryMapper();

    // ---------- cat2 매핑 ----------

    @Test
    @DisplayName("여행 스타일과 직접 이어지는 분류들 — 이것 때문에 cat2로 옮겼다")
    void mapsStyleRelevantCategories() {
        // 기존 11종에서는 아래 셋이 전부 LEISURE 하나로 뭉쳐 스타일을 나눌 수 없었다.
        assertThat(mapper.map("A0202")).isEqualTo(TravelCategory.RELAXATION);   // 휴양 → 휴식형
        assertThat(mapper.map("A0203")).isEqualTo(TravelCategory.EXPERIENCE);   // 체험 → 체험형
        assertThat(mapper.map("A0302")).isEqualTo(TravelCategory.LAND_SPORTS);  // 육상 레포츠 → 활동형
    }

    @Test
    @DisplayName("자연 계열 2종")
    void mapsNature() {
        assertThat(mapper.map("A0101")).isEqualTo(TravelCategory.NATURE_ATTRACTION);
        assertThat(mapper.map("A0102")).isEqualTo(TravelCategory.NATURE_RESOURCE);
    }

    @Test
    @DisplayName("인문 계열 8종")
    void mapsHumanities() {
        assertThat(mapper.map("A0201")).isEqualTo(TravelCategory.HISTORY);
        assertThat(mapper.map("A0204")).isEqualTo(TravelCategory.INDUSTRY);
        assertThat(mapper.map("A0205")).isEqualTo(TravelCategory.ARCHITECTURE);
        assertThat(mapper.map("A0206")).isEqualTo(TravelCategory.CULTURE_FACILITY);
        assertThat(mapper.map("A0207")).isEqualTo(TravelCategory.FESTIVAL);
        assertThat(mapper.map("A0208")).isEqualTo(TravelCategory.PERFORMANCE);
    }

    @Test
    @DisplayName("레포츠 계열 5종")
    void mapsSports() {
        assertThat(mapper.map("A0301")).isEqualTo(TravelCategory.SPORTS_INFO);
        assertThat(mapper.map("A0303")).isEqualTo(TravelCategory.WATER_SPORTS);
        assertThat(mapper.map("A0304")).isEqualTo(TravelCategory.AIR_SPORTS);
        assertThat(mapper.map("A0305")).isEqualTo(TravelCategory.MIXED_SPORTS);
    }

    @Test
    @DisplayName("쇼핑·음식·숙박")
    void mapsShoppingFoodLodging() {
        assertThat(mapper.map("A0401")).isEqualTo(TravelCategory.SHOPPING);
        assertThat(mapper.map("A0502")).isEqualTo(TravelCategory.RESTAURANT);
        assertThat(mapper.map("B0201")).isEqualTo(TravelCategory.LODGING);
    }

    @Test
    @DisplayName("추천코스 6종")
    void mapsCourses() {
        assertThat(mapper.map("C0112")).isEqualTo(TravelCategory.FAMILY_COURSE);
        assertThat(mapper.map("C0113")).isEqualTo(TravelCategory.SOLO_COURSE);
        assertThat(mapper.map("C0114")).isEqualTo(TravelCategory.HEALING_COURSE);
        assertThat(mapper.map("C0115")).isEqualTo(TravelCategory.WALKING_COURSE);
        assertThat(mapper.map("C0116")).isEqualTo(TravelCategory.CAMPING_COURSE);
        assertThat(mapper.map("C0117")).isEqualTo(TravelCategory.FOOD_COURSE);
    }

    @Test
    @DisplayName("TourAPI의 cat2 24종이 하나도 빠짐없이 매핑된다")
    void coversEveryKnownCat2() {
        // 빠뜨리면 그 분류의 장소가 전부 UNKNOWN으로 떨어져 스타일 필터에서 사라진다.
        // 목록은 categoryCode2 실측(2026-08-15)으로 받은 전부다.
        String[] allCat2 = {
                "A0101", "A0102",
                "A0201", "A0202", "A0203", "A0204", "A0205", "A0206", "A0207", "A0208",
                "A0301", "A0302", "A0303", "A0304", "A0305",
                "A0401", "A0502", "B0201",
                "C0112", "C0113", "C0114", "C0115", "C0116", "C0117"};

        assertThat(allCat2).allSatisfy(code ->
                assertThat(mapper.map(code))
                        .as("cat2 %s", code)
                        .isNotEqualTo(TravelCategory.UNKNOWN));
    }

    @Test
    @DisplayName("24종이 서로 다른 값으로 간다 — 둘이 같은 값이면 그만큼 못 나눈다")
    void mapsEachCat2Distinctly() {
        long distinct = Arrays.stream(TravelCategory.values())
                .filter(c -> c != TravelCategory.UNKNOWN)
                .map(TravelCategory::getCat2Code)
                .collect(Collectors.toSet())
                .size();

        assertThat(distinct).isEqualTo(24);
    }

    // ---------- 모르는 값 ----------

    @Test
    @DisplayName("모르는 cat2는 UNKNOWN이다 — 자연으로 둔갑시키지 않는다")
    void fallsBackToUnknown() {
        // 예전에는 기본값이 NATURE라 모르는 것이 조용히 "자연"으로 저장됐다.
        // UNKNOWN이면 "TourAPI가 새 분류를 추가했다"를 나중에 조회로 찾아낼 수 있다.
        assertThat(mapper.map("Z9999")).isEqualTo(TravelCategory.UNKNOWN);
    }

    @Test
    @DisplayName("cat2가 비어 있어도 예외 없이 값을 준다 — category는 NOT NULL이다")
    void neverReturnsNull() {
        // 여기서 null이나 예외가 나오면 장소 하나 때문에 저장이 통째로 실패한다.
        assertThat(mapper.map(null)).isEqualTo(TravelCategory.UNKNOWN);
        assertThat(mapper.map("")).isEqualTo(TravelCategory.UNKNOWN);
        assertThat(mapper.map("   ")).isEqualTo(TravelCategory.UNKNOWN);
    }

    @Test
    @DisplayName("대소문자·공백이 달라도 같게 읽는다")
    void normalizesInput() {
        assertThat(mapper.map(" a0203 ")).isEqualTo(TravelCategory.EXPERIENCE);
    }

    // ---------- 표시명 ----------

    @Test
    @DisplayName("모든 분류에 사람이 읽을 이름이 있다 — 응답의 category로 그대로 나간다")
    void everyCategoryHasDisplayName() {
        assertThat(TravelCategory.values()).allSatisfy(category ->
                assertThat(category.getDisplayName()).as("%s", category).isNotBlank());
    }

    @Test
    @DisplayName("표시명은 TourAPI가 쓰는 이름을 그대로 따른다")
    void usesTourApiNames() {
        // 우리가 지어내면 TourAPI 문서와 대조할 때 매번 번역해야 한다.
        assertThat(TravelCategory.RELAXATION.getDisplayName()).isEqualTo("휴양관광지");
        assertThat(TravelCategory.EXPERIENCE.getDisplayName()).isEqualTo("체험관광지");
        assertThat(TravelCategory.RESTAURANT.getDisplayName()).isEqualTo("음식점");
    }
}
