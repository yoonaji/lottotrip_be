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
 * <pre>
 * 미션 확인 → 회원 확인 → 위치 인증(8-1) → user_missions 저장
 * </pre>
 *
 * <p><b>미션에는 주인이 없다.</b> 코스나 슬롯과 달리 미션은 장소에 붙은 공용 자산이라
 * "내 것인가"를 따지지 않는다. 같은 미션을 여러 사람이 각자 완료할 수 있고,
 * 기록은 {@code (회원, 미션)} 쌍으로 남는다.
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
     * <p><b>위치를 먼저 확인하고 저장한다.</b> 순서가 뒤집히면 인증에 실패한 시도까지 기록이 남고,
     * {@code (user_id, mission_id)} UNIQUE에 걸려 <b>나중에 진짜 도착했을 때 완료할 수 없게 된다.</b>
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

    /**
     * 이미 완료한 미션인지 본다. (roadmap 8-3)
     *
     * <p><b>위치 인증보다 먼저 본다.</b> 이미 끝난 미션에 엉뚱한 좌표로 요청이 오면
     * "위치 인증 실패(422)"가 아니라 "이미 완료됨(409)"이 맞는 설명이다. 순서가 뒤집히면
     * 이미 완료한 사용자에게 <b>다시 가라고 안내하는</b> 응답이 나간다.
     *
     * <p><b>DB의 {@code (user_id, mission_id)} UNIQUE가 최후의 방어선이고, 이 검사는 그 앞단이다.</b>
     * 검사가 없어도 데이터는 지켜지지만, 사용자에게는 원인을 알 수 없는 500이 나간다.
     * 여기서 미리 걸러 명세대로 409로 답한다.
     *
     * <p>⚠️ <b>완전히 동시에 들어온 두 요청은 이 검사만으로 막히지 않는다.</b> 둘 다 "없다"를 보고
     * 지나갈 수 있고, 그때는 뒤늦게 UNIQUE 제약이 막는다(한쪽만 저장된다). 데이터가 깨지지는 않으므로
     * 지금은 여기까지 둔다 — {@code MissionMatcher}의 동시 생성 문제(6-5 ④)와 같은 성격이다.
     */
    private void requireNotCompleted(User user, Mission mission) {
        if (userMissionRepository.existsByUserIdAndMissionId(user.getId(), mission.getId())) {
            log.debug("이미 완료한 미션: userId={}, missionId={}", user.getId(), mission.getId());
            throw new CustomException(ErrorCode.ALREADY_COMPLETED);
        }
    }

    /**
     * 미션이 붙은 장소에 실제로 와 있는지 본다.
     *
     * <p>판정 자체는 {@link MissionLocationVerifier}가 하고, 여기서는 <b>실패를 어떤 에러로
     * 답할지</b>만 정한다. 나눠 두면 "얼마나 가까웠는지 함께 알려주기" 같은 요구가 생겨도
     * 판정 쪽은 그대로 쓸 수 있다.
     */
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

    /**
     * 완료 기록을 남길 회원.
     *
     * <p>토큰이 유효해도 그 회원이 아직 있는지는 별개다(탈퇴). 없는 회원으로 기록을 만들면
     * {@code user_id} FK가 가리킬 곳이 없어 저장 시점에 터진다. 여기서 인증 문제로 돌려준다.
     * 코스(7-1)와 같은 원칙이다.
     */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.debug("존재하지 않는 회원의 미션 완료 요청: userId={}", userId);
                    return new CustomException(ErrorCode.UNAUTHORIZED);
                });
    }
}
