package com.lottotrip.auth.repository;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.entity.SocialAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 소셜 계정 저장소. */
public interface SocialAuthRepository extends JpaRepository<SocialAuth, Long> {

    /**
     * 로그인 시 사람을 찾는 조회. (tour_api_erd.md 1 — social_auth)
     *
     * 메서드 이름만으로 쿼리가 만들어진다. `findBy` 뒤에 필드명을 이어 붙이면
     * 스프링이 `WHERE provider = ? AND provider_user_id = ?`를 생성한다.
     *
     * 없을 수도 있는 결과라 `Optional`로 받는다. 신규 가입 분기를 타야 하는 정상 상황이므로
     * 예외가 아니라 빈 값이어야 한다.
     */
    Optional<SocialAuth> findByProviderAndProviderUserId(ProviderType provider, String providerUserId);

    /**
     * 이 회원의 소셜 연결을 전부 끊는다. 회원 탈퇴 때만 부른다. (roadmap 9-5, 결정 20)
     *
     * **탈퇴에서 유일하게 행을 실제로 지우는 곳이다.** 두 가지 이유가 겹친다.
     *   1. `provider_user_id`와 소셜 토큰은 그 자체로 개인정보다
     *   2. **남겨 두면 같은 소셜 계정으로 다시 로그인했을 때 탈퇴한 계정이 되살아난다** —
     *      로그인은 `provider` + `provider_user_id`로 사람을 찾기 때문이다(결정 4)
     *
     * 지우고 나면 재로그인은 **신규 가입**으로 갈린다. 옛 이력은 익명 껍데기에 남아 연결되지 않는다.
     *
     * `deleteBy...`로 이름을 지으면 스프링이 삭제 쿼리를 만들어 준다. 다만 **트랜잭션 안에서
     * 불러야 한다** — 쓰기 동작이라 트랜잭션이 없으면 예외가 난다.
     */
    void deleteByUserId(Long userId);
}
