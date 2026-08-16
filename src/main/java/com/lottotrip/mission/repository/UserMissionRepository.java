package com.lottotrip.mission.repository;

import com.lottotrip.mission.entity.UserMission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** 미션 수행 기록 저장소. */
public interface UserMissionRepository extends JpaRepository<UserMission, Long> {

    /**
     * 이미 완료한 미션인지 확인한다. 명세의 `409 ALREADY_COMPLETED` 판정에 쓴다.
     *
     * 완료 시점에만 줄이 생기므로 "줄이 있다 = 완료했다"이다.
     */
    boolean existsByUserIdAndMissionId(Long userId, Long missionId);

    /**
     * 이 회원이 완료한 미션 번호를 한 번에 골라낸다. 코스 조회의 `completed` 판정에 쓴다.
     *
     * **왜 {@link #existsByUserIdAndMissionId}를 반복해 부르지 않는가** — 코스에 담긴 항목이
     * 10개면 조회가 10번 나간다(N+1 문제). 목록 조회 1번 + 항목마다 1번씩이라는 뜻이다.
     * 번호를 한꺼번에 넘겨 **한 번만** 묻고, 돌아온 목록에 들어 있는지로 판정한다.
     *
     * 메서드 이름만으로는 "미션 번호만 골라 달라"를 표현할 수 없어 `@Query`로 직접 적었다.
     * `select um.mission.id`는 **필요한 컬럼 하나만** 읽어 온다는 뜻이다 —
     * 기록 전체를 Entity로 불러올 이유가 없다.
     *
     * ⚠️ `missionIds`가 비면 `IN ()`이 되어 DB에 따라 문법 오류가 난다.
     * 부르는 쪽에서 빈 경우를 먼저 걸러야 한다.
     */
    @Query("select um.mission.id from UserMission um "
            + "where um.user.id = :userId and um.mission.id in :missionIds")
    List<Long> findCompletedMissionIds(@Param("userId") Long userId,
                                       @Param("missionIds") Collection<Long> missionIds);
}
