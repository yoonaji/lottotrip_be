package com.lottotrip.mission.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.dto.MissionCompleteRequest;
import com.lottotrip.mission.dto.MissionCompleteResponse;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.entity.UserMission;
import com.lottotrip.mission.repository.MissionRepository;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미션 완료 처리. (roadmap 8단계, tour_api_erd.md 4-5)
 *
 * 미션 확인 → 회원 확인 → 위치 인증(8-1) → user_missions 저장
 *
 * 미션에는 주인이 없다. 코스나 슬롯과 달리 미션은 장소에 붙은 공용 자산이라
 * "내 것인가"를 따지지 않는다. 같은 미션을 여러 사람이 각자 완료할 수 있고,
 * 기록은 `(회원, 미션)` 쌍으로 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;
    private final UserMissionRepository userMissionRepository;
    private final UserRepository userRepository;
    private final MissionLocationVerifier locationVerifier;

    /**
     * 미션을 완료 처리한다. (roadmap 8-2)
     *
     * 위치를 먼저 확인하고 저장한다. 순서가 뒤집히면 인증에 실패한 시도까지 기록이 남고,
     * `(user_id, mission_id)` UNIQUE에 걸려 나중에 진짜 도착했을 때 완료할 수 없게 된다.
     *
     * @throws CustomException 미션이 없으면 {@link ErrorCode#MISSION_NOT_FOUND},
     *                         이미 완료했으면 {@link ErrorCode#ALREADY_COMPLETED},
     *                         반경 밖이면 {@link ErrorCode#VERIFICATION_FAILED},
     *                         회원이 없으면 {@link ErrorCode#UNAUTHORIZED}
     */
    @Transactional
    public MissionCompleteResponse complete(Long userId, Long missionId, MissionCompleteRequest request) {
        Mission mission = findMission(missionId);
        User user = findUser(userId);

        requireNotCompleted(user, mission);
        requireAtPlace(mission, request);

        UserMission record = userMissionRepository.save(UserMission.complete(user, mission));
        return MissionCompleteResponse.from(record);
    }

    /** 이미 완료한 미션인지 확인. (roadmap 8-3) */
    private void requireNotCompleted(User user, Mission mission) {
        if (userMissionRepository.existsByUserIdAndMissionId(user.getId(), mission.getId())) {
            log.debug("이미 완료한 미션: userId={}, missionId={}", user.getId(), mission.getId());
            throw new CustomException(ErrorCode.ALREADY_COMPLETED);
        }
    }

    /** 미션이 붙은 장소에 실제로 와 있는지 본다. 위치 기반 미션 인증 로직 */
    private void requireAtPlace(Mission mission, MissionCompleteRequest request) {
        if (!locationVerifier.isAtPlace(mission.getPlace(), request.latitude(), request.longitude())) {
            log.debug("위치 인증 실패: missionId={}, 요청 좌표=({}, {})",
                    mission.getId(), request.latitude(), request.longitude());
            throw new CustomException(ErrorCode.VERIFICATION_FAILED);
        }
    }

    private Mission findMission(Long missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> {
                    log.debug("존재하지 않는 미션: missionId={}", missionId);
                    return new CustomException(ErrorCode.MISSION_NOT_FOUND);
                });
    }

    /**유저 조회*/
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.debug("존재하지 않는 회원의 미션 완료 요청: userId={}", userId);
                    return new CustomException(ErrorCode.UNAUTHORIZED);
                });
    }
}
