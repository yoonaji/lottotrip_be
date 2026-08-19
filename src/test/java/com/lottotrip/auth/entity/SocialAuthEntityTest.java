package com.lottotrip.auth.entity;

import com.lottotrip.support.PostgresContainerSupport;
import com.lottotrip.user.entity.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `social_auth` 테이블 매핑 검증. (tour_api_erd.md 1 — social_auth)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SocialAuthEntityTest extends PostgresContainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    private User persistedUser() {
        User user = User.create("test@khu.ac.kr", "주노", null);
        entityManager.persist(user);
        return user;
    }

    @Test
    @DisplayName("소셜 계정을 저장하면 연결된 회원을 함께 조회할 수 있다")
    void linksToUser() {
        User user = persistedUser();
        SocialAuth socialAuth = SocialAuth.create(user, ProviderType.KAKAO, "kakao-12345", "at", "rt");

        entityManager.persist(socialAuth);
        entityManager.flush();
        entityManager.clear();

        SocialAuth found = entityManager.find(SocialAuth.class, socialAuth.getId());
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getProviderUserId()).isEqualTo("kakao-12345");
    }

    @Test
    @DisplayName("provider는 숫자가 아니라 이름 문자열로 저장된다")
    void storesProviderAsName() {
        SocialAuth socialAuth = SocialAuth.create(persistedUser(), ProviderType.APPLE, "apple-999", "at", "rt");
        entityManager.persist(socialAuth);
        entityManager.flush();

        // enum을 순서(0,1,2)로 저장하면 나중에 값을 중간에 끼워 넣는 순간 기존 데이터의 의미가 통째로 뒤바뀐다.
        // 이름으로 저장되고 있는지를 DB에서 직접 읽어 확인한다.
        Object stored = entityManager.getEntityManager()
                .createNativeQuery("SELECT provider FROM social_auth WHERE auth_id = :id")
                .setParameter("id", socialAuth.getId())
                .getSingleResult();

        assertThat(stored).hasToString("APPLE");
    }

    @Test
    @DisplayName("같은 provider·providerUserId 조합은 두 번 저장할 수 없다")
    void rejectsDuplicateProviderAccount() {
        // 같은 카카오 계정으로 두 개의 회원이 만들어지면 로그인할 때마다 다른 사람이 된다.
        // 로그인은 provider + providerUserId로 조회하므로(tour_api_erd.md 1), 이 조합은 유일해야 한다.
        entityManager.persist(SocialAuth.create(persistedUser(), ProviderType.GOOGLE, "google-1", "at", "rt"));

        // id를 DB가 매기는 방식(IDENTITY)이라 persist 시점에 INSERT가 곧바로 실행된다.
        // 그래서 예외도 flush()가 아니라 두 번째 persist()에서 터진다.
        assertThatThrownBy(() ->
                entityManager.persist(SocialAuth.create(persistedUser(), ProviderType.GOOGLE, "google-1", "at2", "rt2")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_social_auth_provider_user");
    }

    @Test
    @DisplayName("provider가 다르면 providerUserId가 같아도 저장된다")
    void allowsSameIdOnDifferentProvider() {
        // 카카오의 12345와 구글의 12345는 완전히 다른 사람이다.
        entityManager.persist(SocialAuth.create(persistedUser(), ProviderType.KAKAO, "12345", "at", "rt"));
        entityManager.persist(SocialAuth.create(persistedUser(), ProviderType.GOOGLE, "12345", "at", "rt"));

        entityManager.flush(); // 예외 없이 통과해야 한다
    }

    @Test
    @DisplayName("updatedAt은 저장 시점에 자동으로 기록된다")
    void recordsUpdatedAtAutomatically() {
        SocialAuth socialAuth = SocialAuth.create(persistedUser(), ProviderType.KAKAO, "kakao-1", "at", "rt");

        entityManager.persist(socialAuth);
        entityManager.flush();

        assertThat(socialAuth.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("재로그인 시 소셜 토큰을 새 값으로 교체한다")
    void updatesTokens() {
        // 기존 회원이 다시 로그인하면 회원을 새로 만들지 않고 토큰만 갱신한다. (tour_api_erd.md 1)
        SocialAuth socialAuth = SocialAuth.create(persistedUser(), ProviderType.KAKAO, "kakao-1", "old-at", "old-rt");

        socialAuth.updateTokens("new-at", "new-rt");

        assertThat(socialAuth.getAccessToken()).isEqualTo("new-at");
        assertThat(socialAuth.getRefreshToken()).isEqualTo("new-rt");
    }
}
