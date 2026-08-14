package com.lottotrip.slot.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.mission.service.MissionMatcher;
import com.lottotrip.mission.service.TemplateMissionGenerator;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.PlaceMedia;
import com.lottotrip.place.entity.TravelCategory;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.place.service.NearbyPlaceFinder;
import com.lottotrip.slot.dto.SlotDrawRequest;
import com.lottotrip.slot.dto.SlotDrawResponse;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 슬롯 돌리기 전체 흐름 검증. (roadmap 6-6, tour_api_erd.md 2-4 · 4-2)
 *
 * <p>6-1~6-5에서 만든 부품을 엮는다. 세션 확보 → 반경 조회 → 추첨 → {@code saved_slots} 저장
 * → 미션 매칭 → 응답 조립까지가 한 흐름이다.
 *
 * <p>DB는 진짜를 쓴다. {@code saved_slots}가 실제로 저장돼야 {@code slotId}가 나오고,
 * 그 값이 7단계 코스 추가의 입력이 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SlotDrawServiceTest extends PostgresContainerSupport {

    private static final double CENTER_LAT = 37.7519;
    private static final double CENTER_LNG = 128.8761;

    @Autowired
    private TripSessionRepository tripSessionRepository;
    @Autowired
    private SavedSlotRepository savedSlotRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private PlaceMediaRepository placeMediaRepository;
    @Autowired
    private MissionRepository missionRepository;

    private SlotService slotService;
    private User user;
    private int sequence;

    @BeforeEach
    void setUp() {
        sequence = 0;
        // 난수를 고정하지 않는다. 이 테스트들은 "무엇이 뽑히는가"가 아니라 "흐름이 이어지는가"를
        // 보므로 후보를 1건만 두어 결과를 결정적으로 만든다(추첨 자체는 6-4에서 검증했다).
        slotService = new SlotService(
                tripSessionRepository,
                userRepository,
                new NearbyPlaceFinder(placeRepository),
                new PlaceDrawer(new UniformWeightPolicy()),
                new MissionMatcher(missionRepository, new TemplateMissionGenerator()),
                savedSlotRepository,
                placeMediaRepository);
        user = userRepository.save(User.create("a@test.com", "테스터", null));
    }

    private SlotDrawRequest walkRequest() {
        return new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "walk");
    }

    private Place placeAt(String name, double latitude, double longitude) {
        return placeRepository.save(Place.builder()
                .contentId("test-" + (++sequence))
                .contentTypeId("12")
                .name(name)
                .category(TravelCategory.BEACH)
                .address("강원특별자치도 강릉시")
                .latitude(latitude)
                .longitude(longitude)
                .build());
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("반경 안 장소를 뽑아 슬롯을 만든다")
    void drawsPlaceWithinRadius() {
        Place place = placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.slotId()).isNotNull();
        assertThat(response.place().placeId()).isEqualTo(place.getId());
        assertThat(response.place().name()).isEqualTo("사천진해변");
        assertThat(response.place().latitude()).isEqualTo(place.getLatitude());
    }

    @Test
    @DisplayName("카테고리는 한글 이름으로 나간다")
    void exposesCategoryAsDisplayName() {
        // DB에는 BEACH로 저장하지만 응답에는 "해변"으로 나가야 한다(ERD 4-2 예시).
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().category()).isEqualTo("해변");
    }

    @Test
    @DisplayName("거리를 소수 첫째 자리까지 담는다")
    void roundsDistance() {
        // 위도 0.01도 ≈ 1.11km → 1.1로 나간다. 소수점을 그대로 흘리면
        // 응답에 1.1119492636...처럼 의미 없는 자릿수가 나간다.
        placeAt("가까운 곳", CENTER_LAT + 0.01, CENTER_LNG);

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().distanceKm()).isCloseTo(1.1, within(0.001));
    }

    @Test
    @DisplayName("뽑은 결과를 saved_slots에 남긴다 — 코스 추가가 이 slotId를 쓴다")
    void persistsSavedSlot() {
        Place place = placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(savedSlotRepository.findById(response.slotId())).isPresent()
                .get()
                .satisfies(slot -> {
                    assertThat(slot.getPlace().getId()).isEqualTo(place.getId());
                    assertThat(slot.getSession().getUser().getId()).isEqualTo(user.getId());
                });
    }

    @Test
    @DisplayName("미션을 함께 준다 — 없으면 만들어서라도 채운다")
    void attachesMission() {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.mission()).isNotNull();
        assertThat(response.mission().missionId()).isNotNull();
        assertThat(response.mission().title()).isNotBlank();
    }

    @Test
    @DisplayName("대표 이미지가 있으면 thumbnailUrl로 준다")
    void exposesThumbnail() {
        Place place = placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);
        placeMediaRepository.save(PlaceMedia.create(
                place, "https://cdn.example.com/beach.jpg", com.lottotrip.common.enums.MediaType.IMAGE));

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().thumbnailUrl()).isEqualTo("https://cdn.example.com/beach.jpg");
    }

    @Test
    @DisplayName("대표 이미지가 없으면 thumbnailUrl은 null이다")
    void allowsMissingThumbnail() {
        // 실측 채움률이 18%다. 없는 것이 정상이므로 실패시키지 않는다.
        placeAt("이미지 없는 곳", CENTER_LAT + 0.01, CENTER_LNG);

        SlotDrawResponse response = slotService.draw(user.getId(), walkRequest());

        assertThat(response.place().thumbnailUrl()).isNull();
    }

    // ---------- 세션 ----------

    @Test
    @DisplayName("연달아 돌리면 같은 세션에 쌓인다")
    void reusesSessionAcrossDraws() {
        placeAt("A", CENTER_LAT + 0.01, CENTER_LNG);
        placeAt("B", CENTER_LAT + 0.02, CENTER_LNG);

        slotService.draw(user.getId(), walkRequest());
        slotService.draw(user.getId(), walkRequest());

        assertThat(tripSessionRepository.findAll()).hasSize(1);
        assertThat(savedSlotRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("이동수단에 따라 반경이 달라진다 — walk 10km / car 30km")
    void radiusFollowsTransport() {
        placeAt("20km 지점", CENTER_LAT + (20.0 / 111.19), CENTER_LNG);

        // walk(10km)로는 후보가 없다.
        assertThatThrownBy(() -> slotService.draw(user.getId(), walkRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_PLACE_FOUND);

        // car(30km)면 잡힌다. 세션이 walk로 이미 만들어졌으므로 다른 회원으로 확인한다.
        User carUser = userRepository.save(User.create("b@test.com", "차", null));
        SlotDrawResponse response = slotService.draw(
                carUser.getId(), new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "car"));

        assertThat(response.place().name()).isEqualTo("20km 지점");
    }

    // ---------- 오류 ----------

    @Test
    @DisplayName("반경 안에 후보가 없으면 NO_PLACE_FOUND")
    void failsWhenNoCandidate() {
        // 강원 밖 좌표로 돌리면 실제로 이 상황이 된다(적재가 강원 한정).
        // 2026-08-14 사용자 결정: 폴백 없이 404를 그대로 내보낸다.
        placeAt("멀리", CENTER_LAT + 1.0, CENTER_LNG + 1.0);

        assertThatThrownBy(() -> slotService.draw(user.getId(), walkRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_PLACE_FOUND);
    }

    @Test
    @DisplayName("후보가 없으면 슬롯을 저장하지 않는다")
    void doesNotSaveSlotWhenNoCandidate() {
        assertThatThrownBy(() -> slotService.draw(user.getId(), walkRequest()))
                .isInstanceOf(CustomException.class);

        assertThat(savedSlotRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("정의되지 않은 이동수단은 400")
    void rejectsUnknownTransport() {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        assertThatThrownBy(() -> slotService.draw(
                user.getId(), new SlotDrawRequest(CENTER_LAT, CENTER_LNG, 50_000, "bike")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("탈퇴한 회원의 토큰이면 401")
    void rejectsMissingUser() {
        placeAt("사천진해변", CENTER_LAT + 0.01, CENTER_LNG);

        assertThatThrownBy(() -> slotService.draw(999_999L, walkRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }
}
