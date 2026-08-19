package com.lottotrip.auth.repository;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.entity.SocialAuth;
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
 * 로그인 시 사람을 찾는 조회 검증. (tour_api_erd.md 1 — social_auth)
 *
 * 로그인은 이메일이 아니라 `provider + providerUserId`로 사람을 찾는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SocialAuthRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private SocialAuthRepository socialAuthRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistedUser() {
        User user = User.create("test@khu.ac.kr", "주노", null);
        entityManager.persist(user);
        return user;
    }

    @Test
    @DisplayName("provider와 providerUserId로 소셜 계정을 찾는다")
    void findsByProviderAndProviderUserId() {
        User user = persistedUser();
        entityManager.persist(SocialAuth.create(user, ProviderType.KAKAO, "kakao-12345", "at", "rt"));
        entityManager.flush();

        var found = socialAuthRepository.findByProviderAndProviderUserId(ProviderType.KAKAO, "kakao-12345");

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("가입한 적 없는 계정이면 비어 있는 결과를 준다")
    void returnsEmptyForUnknownAccount() {
        // 신규 가입 분기를 타야 하는 상황이다. 예외가 아니라 빈 값으로 받아야 서비스가 분기할 수 있다.
        var found = socialAuthRepository.findByProviderAndProviderUserId(ProviderType.APPLE, "없는-아이디");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("providerUserId가 같아도 provider가 다르면 다른 사람으로 본다")
    void distinguishesByProvider() {
        entityManager.persist(SocialAuth.create(persistedUser(), ProviderType.KAKAO, "12345", "at", "rt"));
        entityManager.flush();

        // 카카오의 12345와 구글의 12345는 완전히 다른 사람이다.
        assertThat(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.GOOGLE, "12345")).isEmpty();
        assertThat(socialAuthRepository.findByProviderAndProviderUserId(ProviderType.KAKAO, "12345")).isPresent();
    }
}
