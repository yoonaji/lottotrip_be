package com.lottotrip.mission.dto;

import com.lottotrip.mission.entity.UserMission;

import java.time.LocalDateTime;

/**
 * 미션 완료 결과. (tour_api_erd.md 4-5)
 *
 * **`completed`는 항상 true다.** 인증에 실패하면 이 응답이 아니라
 * `422 VERIFICATION_FAILED`가 나가기 때문이다. 그럼에도 필드를 두는 이유는
 * 명세가 그렇게 정의돼 있고, 프론트가 성공·실패를 본문으로도 확인할 수 있어서다.
 *
 * @param completedAt 완료 시각. `user_missions.certified_at`이 그대로 나간다
 */
public record MissionCompleteResponse(Long missionId, boolean completed, LocalDateTime completedAt) {

    public static MissionCompleteResponse from(UserMission record) {
        return new MissionCompleteResponse(
                record.getMission().getId(), true, record.getCertifiedAt());
    }
}
