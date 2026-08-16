package com.lottotrip.user.repository;

import com.lottotrip.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
