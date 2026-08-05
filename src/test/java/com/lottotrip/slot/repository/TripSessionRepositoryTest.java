package com.lottotrip.slot.repository;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.slot.entity.TransportType;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 find-or-create의 "find" 부분 검증. (tour_api_erd.md 2-1)
 *
 * <p>서버는 슬롯 요청마다 "이 회원의 가장 최근 세션"을 찾아 12시간 이내인지 본다.
 * 여기서는 조회만 확인하고, 12시간 판정은 6-1에서 붙인다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripSessionRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private TripSessionRepository tripSessionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistedUser() {
        User user = User.create("test@khu.ac.kr", "주노", null);
        entityManager.persist(user);
        return user;
    }

    private TripSession persistedSession(User user, TransportType transport) {
        TripSession session = TripSession.create(user, BudgetLevel.MEDIUM, transport, 37.7519, 128.8761);
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    @Test
    @DisplayName("회원의 세션이 하나면 그것을 찾는다")
    void findsOnlySession() {
        User user = persistedUser();
        TripSession session = persistedSession(user, TransportType.WALK);

        var found = tripSessionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(session.getId());
    }

    @Test
    @DisplayName("세션이 여러 개면 가장 최근 것을 찾는다")
    void findsMostRecentSession() throws InterruptedException {
        User user = persistedUser();
        persistedSession(user, TransportType.WALK);
        // createdAt은 저장 시점에 자동으로 들어간다. 연속 저장하면 두 시각이 같아질 수 있어
        // 정렬 결과가 흔들린다. 잠깐 띄워 시각을 확실히 구분한다.
        Thread.sleep(10);
        TripSession latest = persistedSession(user, TransportType.CAR);

        var found = tripSessionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(latest.getId());
        assertThat(found.get().getTransportation()).isEqualTo(TransportType.CAR);
    }

    @Test
    @DisplayName("세션이 없으면 비어 있는 결과를 준다")
    void returnsEmptyWhenNoSession() {
        // 새 세션을 만들어야 하는 상황이다.
        var found = tripSessionRepository.findTopByUserIdOrderByCreatedAtDesc(persistedUser().getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("다른 회원의 세션은 찾지 않는다")
    void doesNotFindOtherUsersSession() {
        User other = persistedUser();
        persistedSession(other, TransportType.WALK);
        User me = persistedUser();

        assertThat(tripSessionRepository.findTopByUserIdOrderByCreatedAtDesc(me.getId())).isEmpty();
    }
}
