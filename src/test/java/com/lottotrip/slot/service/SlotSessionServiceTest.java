package com.lottotrip.slot.service;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.slot.dto.SlotDrawRequest;
import com.lottotrip.slot.entity.TransportType;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 세션 find-or-create 검증. (roadmap 6-1, tour_api_erd.md 결정 1)
 *
 * **프론트는 세션의 존재를 모른다.** `sessionId`를 보내지 않으므로, 서버가 회원 기준으로
 * "12시간 이내 세션이 있는가"를 매 슬롯 요청마다 판단해 재사용하거나 새로 만든다.
 *
 * DB는 진짜를 쓴다. 재사용 판정이 `created_at` 정렬 조회에 기대고 있어,
 * 시간이 실제로 저장되고 실제로 정렬되어야 의미가 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SlotSessionServiceTest extends PostgresContainerSupport {

    @Autowired
    private TripSessionRepository tripSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private SlotService slotService;
    private User user;

    @BeforeEach
    void setUp() {
        // 이 테스트는 세션 확보만 본다. 추첨·미션 쪽 의존은 getOrCreateActiveSession이
        // 건드리지 않으므로 넘기지 않는다. 실수로 쓰이면 즉시 NPE로 드러난다.
        slotService = new SlotService(tripSessionRepository, userRepository,
                null, null, null, null, null);
        user = userRepository.save(User.create("a@test.com", "테스터", null));
    }

    /** 강릉 좌표 / 5만원 / 도보 기준 요청. */
    private SlotDrawRequest walkRequest() {
        return new SlotDrawRequest(37.7519, 128.8761, 50_000, "walk", null, null);
    }

    /**
     * 세션의 생성 시각을 과거로 민다.
     *
     * `createdAt`은 `@CreationTimestamp`라 저장 시점에 자동으로 채워진다.
     * "12시간이 지난 세션"을 만들려면 저장한 뒤 시각을 직접 바꾸는 수밖에 없다.
     * 영속성 컨텍스트에 남은 값이 DB와 어긋나지 않도록 `clear()`로 비운다.
     */
    private void ageSession(TripSession session, int hoursAgo) {
        em.flush();
        em.createNativeQuery("UPDATE trip_sessions SET created_at = ?1 WHERE session_id = ?2")
                .setParameter(1, LocalDateTime.now().minusHours(hoursAgo))
                .setParameter(2, session.getId())
                .executeUpdate();
        em.clear();
    }

    // ---------- 새로 만드는 경우 ----------

    @Test
    @DisplayName("세션이 하나도 없으면 새로 만든다")
    void createsSessionWhenNoneExists() {
        TripSession session = slotService.getOrCreateActiveSession(user.getId(), walkRequest());

        assertThat(session.getId()).isNotNull();
        assertThat(session.getUser().getId()).isEqualTo(user.getId());
        assertThat(tripSessionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("요청 값이 세션에 그대로 담긴다")
    void storesRequestValuesOnNewSession() {
        TripSession session = slotService.getOrCreateActiveSession(user.getId(), walkRequest());

        assertThat(session.getBudgetRange()).isEqualTo(BudgetLevel.from(50_000));
        assertThat(session.getTransportation()).isEqualTo(TransportType.WALK);
        assertThat(session.getAccommodationLatitude()).isEqualTo(37.7519);
        assertThat(session.getAccommodationLongitude()).isEqualTo(128.8761);
    }

    @Test
    @DisplayName("반경은 요청이 아니라 이동수단에서 온다 — walk 10km / car 30km")
    void derivesRadiusFromTransport() {
        // 반경은 요청 파라미터가 아니다(결정 2). 프론트가 보내지 않으므로 서버가 채운다.
        TripSession walk = slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        assertThat(walk.getSearchRadiusKm()).isEqualTo(10);

        User another = userRepository.save(User.create("b@test.com", "테스터2", null));
        TripSession car = slotService.getOrCreateActiveSession(
                another.getId(), new SlotDrawRequest(37.7519, 128.8761, 50_000, "car", null, null));
        assertThat(car.getSearchRadiusKm()).isEqualTo(30);
    }

    // ---------- 재사용하는 경우 ----------

    @Test
    @DisplayName("12시간 이내 세션이 있으면 그것을 다시 쓴다")
    void reusesRecentSession() {
        TripSession first = slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        ageSession(first, 11);

        TripSession second = slotService.getOrCreateActiveSession(user.getId(), walkRequest());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(tripSessionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("재사용할 때 세션의 파라미터를 갱신하지 않는다 — 첫 슬롯 기준 참고값이다")
    void doesNotUpdateParametersOnReuse() {
        // 결정 1의 A안. 같은 세션에서 조건을 바꿔 다시 돌려도 세션 값은 첫 슬롯 그대로 둔다.
        // 각 슬롯의 실제 조건은 saved_slots가 개별 보존하므로 여기서 덮어쓸 이유가 없고,
        // 덮어쓰면 "이 세션은 무슨 조건이었나"를 아무도 알 수 없게 된다.
        TripSession first = slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        ageSession(first, 1);

        TripSession reused = slotService.getOrCreateActiveSession(
                user.getId(), new SlotDrawRequest(38.2070, 128.5918, 300_000, "car", null, null));

        assertThat(reused.getId()).isEqualTo(first.getId());
        assertThat(reused.getTransportation()).isEqualTo(TransportType.WALK);
        assertThat(reused.getSearchRadiusKm()).isEqualTo(10);
        assertThat(reused.getBudgetRange()).isEqualTo(BudgetLevel.from(50_000));
        assertThat(reused.getAccommodationLatitude()).isEqualTo(37.7519);
    }

    @Test
    @DisplayName("12시간이 지난 세션은 새로 만든다")
    void createsNewSessionWhenExpired() {
        TripSession old = slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        ageSession(old, 13);

        TripSession fresh = slotService.getOrCreateActiveSession(user.getId(), walkRequest());

        assertThat(fresh.getId()).isNotEqualTo(old.getId());
        assertThat(tripSessionRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("만료된 세션이 남아 있어도 가장 최근 것만 본다")
    void looksOnlyAtMostRecentSession() {
        // 오래된 세션이 쌓여 있어도 판정 대상은 마지막 하나다.
        // 전체를 훑으면 12시간 지난 세션을 되살려 쓰는 일이 생긴다.
        TripSession old = slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        ageSession(old, 20);
        TripSession recent = slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        ageSession(recent, 2);

        TripSession result = slotService.getOrCreateActiveSession(user.getId(), walkRequest());

        assertThat(result.getId()).isEqualTo(recent.getId());
    }

    @Test
    @DisplayName("다른 회원의 세션은 재사용하지 않는다")
    void doesNotReuseAnotherUsersSession() {
        slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        User other = userRepository.save(User.create("c@test.com", "남", null));

        TripSession session = slotService.getOrCreateActiveSession(other.getId(), walkRequest());

        assertThat(session.getUser().getId()).isEqualTo(other.getId());
        assertThat(tripSessionRepository.findAll()).hasSize(2);
    }

    // ---------- 경계와 오류 ----------

    @Test
    @DisplayName("정확히 12시간을 넘긴 세션은 만료로 본다")
    void treatsExactlyTwelveHoursAsExpired() {
        // 경계를 열어 두면 "12시간 정각"이 재사용될지 아닐지가 실행 순간의 밀리초에 달린다.
        TripSession old = slotService.getOrCreateActiveSession(user.getId(), walkRequest());
        ageSession(old, 12);

        TripSession result = slotService.getOrCreateActiveSession(user.getId(), walkRequest());

        assertThat(result.getId()).isNotEqualTo(old.getId());
    }

    @Test
    @DisplayName("정의되지 않은 이동수단은 400으로 거절한다")
    void rejectsUnknownTransport() {
        // 여기서 걸러 내지 않으면 "조건에 맞는 장소가 없다"는 뜻의 404가 나가서
        // 잘못 보낸 쪽이 원인을 알기 어렵다.
        SlotDrawRequest request = new SlotDrawRequest(37.7519, 128.8761, 50_000, "bike", null, null);

        assertThatThrownBy(() -> slotService.getOrCreateActiveSession(user.getId(), request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("탈퇴한 회원의 토큰으로는 세션을 만들지 않는다")
    void rejectsMissingUser() {
        // 토큰이 유효해도 그 회원이 아직 있는지는 별개다. 없는 회원으로 세션을 만들면
        // user_id FK가 가리킬 곳이 없어 저장 시점에 터진다.
        assertThatThrownBy(() -> slotService.getOrCreateActiveSession(999_999L, walkRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }
}
