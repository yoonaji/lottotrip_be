package com.lottotrip.slot.repository;

import com.lottotrip.slot.entity.TripSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 여행 세션 저장소. */
public interface TripSessionRepository extends JpaRepository<TripSession, Long> {

    /**
     * 이 회원의 가장 최근 세션을 찾는다. (tour_api_erd.md 2-1)
     *
     * `Top`은 "제일 위 하나만", `OrderByCreatedAtDesc`는 "생성 시각 내림차순"이다.
     * 합치면 "가장 최근 것 하나"가 된다.
     *
     * 찾은 세션이 12시간 이내인지 판단하는 것은 서비스의 몫이다(6-1).
     * 저장소는 "가장 최근 것"만 돌려준다.
     */
    Optional<TripSession> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
