package com.lottotrip.user.repository;

import com.lottotrip.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 회원 저장소.
 *
 * `JpaRepository`를 상속하기만 하면 스프링이 구현체를 자동으로 만들어 준다.
 * save·findById·delete 같은 기본 동작은 따로 작성하지 않아도 쓸 수 있다.
 *
 * 로그인은 회원을 이메일이 아니라 소셜 계정으로 찾으므로
 * ({@link com.lottotrip.auth.repository.SocialAuthRepository}) 여기에 조회 메서드를 두지 않는다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * **살아 있는 회원만** 찾는다. (roadmap 9-5, 결정 20)
     *
     * ⚠️ **`findById`를 쓰면 탈퇴한 회원이 그대로 통과한다.** 소프트 삭제라 행이 남기 때문이다.
     * 회원을 조회하는 곳은 전부 이 메서드를 써야 한다. 조건 하나가 붙을 뿐이라
     * 추가 조회 비용은 없다.
     */
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /** 살아 있는 회원인지만 확인한다. 토큰 갱신처럼 회원 객체가 필요 없는 곳에서 쓴다. */
    boolean existsByIdAndDeletedAtIsNull(Long id);
}
