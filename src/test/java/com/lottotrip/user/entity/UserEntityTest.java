package com.lottotrip.user.entity;

import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code users} 테이블 매핑 검증. (tour_api_erd.md 1 — users)
 *
 * <p>{@code @DataJpaTest}는 JPA에 필요한 것만 띄운다(컨트롤러·시큐리티는 뜨지 않는다).
 * 테스트마다 트랜잭션을 열고 끝나면 되돌리므로, 앞 테스트가 넣은 데이터가 뒤 테스트에 남지 않는다.
 *
 * <p>{@code replace = NONE}을 준 이유: 그냥 두면 스프링이 클래스패스의 H2로 DataSource를 바꿔치기한다.
 * 우리는 운영과 같은 PostgreSQL로 확인해야 하므로 바꿔치기를 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserEntityTest extends PostgresContainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("회원을 저장하면 userId가 자동으로 부여된다")
    void assignsIdOnPersist() {
        User user = User.create("test@khu.ac.kr", "주노", "https://cdn.example.com/p.png");

        entityManager.persist(user);
        entityManager.flush();

        assertThat(user.getId()).isNotNull();
    }

    @Test
    @DisplayName("createdAt은 저장 시점에 자동으로 기록된다")
    void recordsCreatedAtAutomatically() {
        User user = User.create("test@khu.ac.kr", "주노", null);

        entityManager.persist(user);
        entityManager.flush();

        // 가입 시각을 서비스 코드에서 매번 넣어 주면 빠뜨릴 수 있다. JPA가 채우게 한다.
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이메일이 없어도 저장된다 — 애플 Private Relay 대응")
    void allowsNullEmail() {
        // 애플 로그인은 사용자가 이메일 가리기를 선택하면 이메일을 주지 않는다.
        // email을 NOT NULL로 잡으면 애플 로그인 자체가 막힌다. (tour_api_erd.md 1 — users)
        User user = User.create(null, "익명 여행자", null);

        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User found = entityManager.find(User.class, user.getId());
        assertThat(found.getEmail()).isNull();
        assertThat(found.getNickname()).isEqualTo("익명 여행자");
    }

    @Test
    @DisplayName("저장한 값이 그대로 다시 조회된다")
    void persistsAllFields() {
        User user = User.create("najoonho@khu.ac.kr", "주노", "https://cdn.example.com/p.png");

        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear(); // 영속성 컨텍스트를 비워야 캐시가 아닌 DB에서 다시 읽는다

        User found = entityManager.find(User.class, user.getId());
        assertThat(found.getEmail()).isEqualTo("najoonho@khu.ac.kr");
        assertThat(found.getNickname()).isEqualTo("주노");
        assertThat(found.getProfileImageUrl()).isEqualTo("https://cdn.example.com/p.png");
    }
}
