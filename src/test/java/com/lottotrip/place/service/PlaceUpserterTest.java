package com.lottotrip.place.service;

import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.PlaceMedia;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.place.tourapi.TourApiPlaceItem;
import com.lottotrip.place.tourapi.TravelCategoryMapper;
import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 뽑힌 장소를 담는 일 검증. (roadmap 6-12, 결정 12)
 *
 * 온디맨드에서는 **추첨된 1건만** 저장한다. 배치가 강원 전역을 미리 담던 자리를 대신하므로,
 * 여기서 저장에 실패하면 그 장소는 사용자에게 영영 나가지 못한다.
 *
 * DB는 진짜를 쓴다. "두 번 뽑혀도 중복되지 않는다"는 실제 UNIQUE 제약이 있어야 증명되고,
 * "좌표가 없으면 못 담는다"도 NOT NULL이 실재해야 의미가 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlaceUpserterTest extends PostgresContainerSupport {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceMediaRepository placeMediaRepository;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CityRepository cityRepository;

    private PlaceUpserter upserter;

    @BeforeEach
    void setUp() {
        upserter = new PlaceUpserter(placeRepository, placeMediaRepository,
                stateRepository, cityRepository, new TravelCategoryMapper());
    }

    /** 지역 시드가 이미 돌아간 상태. 장소를 시·군에 이으려면 이게 먼저다. */
    private void seededGangwon() {
        State gangwon = stateRepository.save(State.create("강원", "32"));
        cityRepository.save(City.create(gangwon, "강릉시", "1"));
    }

    private TourApiPlaceItem item(String contentId, String title) {
        return item(contentId, title, "12", "128.8954", "37.8021", "");
    }

    private TourApiPlaceItem item(String contentId, String title, String contentTypeId,
                                  String mapx, String mapy, String firstImage) {
        return new TourApiPlaceItem(
                contentId, contentTypeId, title, "강원특별자치도 강릉시 사천면", "",
                "32", "1", "A01", "A0101", "A01011200",
                firstImage, "", mapx, mapy, "442.3", "20240115103045");
    }

    // ---------- 담기 ----------

    @Test
    @DisplayName("뽑힌 장소를 담고 그 장소를 돌려준다 — 슬롯이 이어 붙일 수 있어야 한다")
    void savesAndReturnsPlace() {
        seededGangwon();

        Place saved = upserter.upsert(item("126508", "사천진해변"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("사천진해변");
        assertThat(saved.getContentId()).isEqualTo("126508");
        assertThat(placeRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("분류를 매핑해 담는다 — cat2(A0101)를 그대로 옮긴다")
    void mapsCategory() {
        seededGangwon();

        Place saved = upserter.upsert(item("126508", "사천진해변"));

        assertThat(saved.getCategory()).isEqualTo(TravelCategory.NATURE_ATTRACTION);
    }

    @Test
    @DisplayName("시·군에 잇는다 — 지역 시드가 먼저 돌아 있어야 한다")
    void linksCity() {
        seededGangwon();

        Place saved = upserter.upsert(item("126508", "사천진해변"));

        assertThat(saved.getCity()).isNotNull();
        assertThat(saved.getCity().getCityName()).isEqualTo("강릉시");
    }

    @Test
    @DisplayName("시·군을 못 찾아도 장소는 담는다 — 지역 시드가 늦어도 슬롯은 돌아야 한다")
    void savesEvenWhenCityUnknown() {
        // 여기서 멈추면 지역 표가 비어 있는 동안 슬롯 전체가 죽는다. city_id가 nullable인 이유다.
        Place saved = upserter.upsert(item("126508", "사천진해변"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCity()).isNull();
    }

    // ---------- 다시 뽑혔을 때 ----------

    @Test
    @DisplayName("같은 장소가 또 뽑히면 새로 만들지 않고 갱신한다")
    void updatesInsteadOfDuplicating() {
        // 온디맨드에서는 인기 있는 장소가 반복해서 뽑힌다. 막지 않으면 같은 곳이 계속 쌓인다.
        seededGangwon();
        upserter.upsert(item("126508", "사천진해변"));

        Place again = upserter.upsert(item("126508", "사천진해변(개명)"));

        assertThat(placeRepository.findAll()).hasSize(1);
        assertThat(again.getName()).isEqualTo("사천진해변(개명)");
    }

    @Test
    @DisplayName("같은 장소를 다시 담아도 이미지가 늘어나지 않는다")
    void doesNotDuplicateThumbnail() {
        seededGangwon();
        upserter.upsert(item("126508", "사천진해변", "12", "128.8954", "37.8021", "https://cdn/a.jpg"));

        upserter.upsert(item("126508", "사천진해변", "12", "128.8954", "37.8021", "https://cdn/a.jpg"));

        assertThat(placeMediaRepository.findAll()).hasSize(1);
    }

    // ---------- 이미지 ----------

    @Test
    @DisplayName("대표 이미지를 함께 담는다 — 목록 응답에만 딸려 오므로 지금 담아야 한다")
    void savesThumbnail() {
        seededGangwon();

        Place saved = upserter.upsert(
                item("126508", "사천진해변", "12", "128.8954", "37.8021", "https://cdn/beach.jpg"));

        assertThat(placeMediaRepository.findAll())
                .extracting(PlaceMedia::getMediaUrl)
                .containsExactly("https://cdn/beach.jpg");
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("이미지가 없으면 행을 만들지 않는다 — 빈 URL이 응답에 나가면 안 된다")
    void skipsEmptyThumbnail() {
        seededGangwon();

        upserter.upsert(item("126508", "사천진해변", "12", "128.8954", "37.8021", ""));

        assertThat(placeMediaRepository.findAll()).isEmpty();
    }

    // ---------- 담을 수 없는 것 ----------

    @Test
    @DisplayName("좌표가 없으면 거절한다 — 뽑히기 전에 걸러졌어야 한다")
    void rejectsPlaceWithoutCoordinate() {
        // latitude·longitude는 NOT NULL이다. 여기까지 왔다는 것은 선정기가 걸러야 할 것을
        // 걸러내지 못했다는 뜻이라, 조용히 넘기지 않고 우리 코드의 버그로 드러낸다.
        seededGangwon();

        assertThatThrownBy(() -> upserter.upsert(
                item("126508", "좌표없음", "12", null, null, "")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
