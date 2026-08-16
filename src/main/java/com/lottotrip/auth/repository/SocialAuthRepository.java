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
}
