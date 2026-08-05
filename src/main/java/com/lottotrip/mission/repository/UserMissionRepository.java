package com.lottotrip.mission.repository;

import com.lottotrip.mission.entity.UserMission;
import org.springframework.data.jpa.repository.JpaRepository;

/** 미션 수행 기록 저장소. */
public interface UserMissionRepository extends JpaRepository<UserMission, Long> {

    /**
     * 이미 완료한 미션인지 확인한다. 명세의 {@code 409 ALREADY_COMPLETED} 판정에 쓴다.
     *
     * <p>완료 시점에만 줄이 생기므로 "줄이 있다 = 완료했다"이다.
     */
    boolean existsByUserIdAndMissionId(Long userId, Long missionId);
}
