package com.lottotrip.auth.service;

import com.lottotrip.auth.dto.WithdrawResponse;
import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.entity.UserMission;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 (소프트 삭제). (roadmap 9-5, 결정 20)
 *
 * 회원 행은 남기고 개인정보만 지운다. `users`를 참조하는 FK 네 개가 `NO ACTION`이라
 * 행을 지우면 제약 위반이고, CASCADE로 바꾸면 여행·미션 이력이 통째로 사라진다.
 * 다만 플래그만 세우면 개인정보가 남아 앱스토어 심사(5.1.1(v))도 파기 의무도 만족하지 못한다.
 *
 * 지운다 : 이메일·닉네임·프로필 / 소셜 계정 행 / 숙소 좌표 / 미션 인증 사진
 * 남긴다 : 회원 행(익명)·가입 시각 / 여행 코스·슬롯 / 예산·이동수단 / 미션 완료 이력
 *
 * 여러 도메인을 한 번에 손대므로 `AuthService`와 파일을 나눴다.
 *
 * ⛔ 애플 연동 해제(revoke)는 미구현(9-5-4). 팀 ID·키 ID·`.p8`와 로그인 시
 * `authorizationCode`가 필요하다. 붙일 자리는 파기 앞(소셜 토큰이 살아 있는 시점)이고,
 * 실패해도 탈퇴는 진행해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final SocialAuthRepository socialAuthRepository;
    private final TripSessionRepository tripSessionRepository;
    private final UserMissionRepository userMissionRepository;

    /**
     * 탈퇴 처리.
     *
     * `@Transactional`이라 네 가지 파기가 전부 성공하거나 전부 취소된다. 중간에 실패해
     * "소셜 연결은 끊겼는데 이메일은 남은" 상태가 되면 재로그인도 못 하고 개인정보는 남는다.
     * `save`를 부르지 않는 것은 더티 체킹 덕분이다 — 트랜잭션이 끝날 때 JPA가 UPDATE를 내보낸다.
     *
     * @throws CustomException 이미 탈퇴했거나 없는 회원이면 {@link ErrorCode#UNAUTHORIZED}
     */
    @Transactional
    public WithdrawResponse withdraw(Long userId) {
        // 탈퇴자를 조회 단계에서 거른다. findById를 쓰면 이미 탈퇴한 회원이 통과해
        // 탈퇴 시각이 덮이고 "언제 탈퇴했는가"가 흐려진다.
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.debug("탈퇴 대상이 아닌 회원의 탈퇴 요청: userId={}", userId);
                    return new CustomException(ErrorCode.UNAUTHORIZED);
                });

        erasePersonalData(userId, user);

        log.info("회원 탈퇴: userId={}", userId);
        return WithdrawResponse.of(user.getDeletedAt());
    }

    /**
     * 흩어져 있는 개인정보를 모두 지운다.
     *
     * 회원 번호는 인자로 받은 값을 쓴다. 방금 그 번호로 찾아온 회원이라 같은 값이고,
     * 엔티티에서 다시 꺼내면 "ID가 채워져 있는가"에 의존하게 된다.
     */
    private void erasePersonalData(Long userId, User user) {
        // ① 소셜 연결 — 유일하게 행을 실제로 지우는 곳이다.
        //    남기면 같은 소셜 계정으로 로그인했을 때 탈퇴 계정이 되살아난다.
        socialAuthRepository.deleteByUserId(userId);

        // ② 숙소 좌표 — 위치정보다. 세션 행 자체는 통계로 남긴다.
        for (TripSession session : tripSessionRepository.findAllByUserId(userId)) {
            session.eraseAccommodationLocation();
        }

        // ③ 미션 인증 사진 — 본인이 찍혔을 수 있다. 완료 이력은 남긴다.
        for (UserMission userMission : userMissionRepository.findAllByUserId(userId)) {
            userMission.eraseCertifiedMedia();
        }

        // ④ 회원의 식별정보 — 이메일·닉네임·프로필 사진을 지우고 탈퇴 시각을 남긴다.
        user.withdraw();
    }
}
