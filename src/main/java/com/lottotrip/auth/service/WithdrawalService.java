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
 * 회원 탈퇴. (roadmap 9-5, 결정 20)
 *
 * ## 왜 별도 서비스인가
 * 탈퇴는 **여러 도메인의 데이터를 한 번에 손댄다** — 회원·소셜계정·세션·미션 기록.
 * `AuthService`에 넣으면 로그인/갱신/로그아웃만 있던 클래스가 갑자기 슬롯·미션 저장소까지 알게 된다.
 * 성격이 다른 흐름이라 파일을 나눴다.
 *
 * ## 소프트 삭제 — 남기는 것과 지우는 것
 * 행을 지우지 않는 이유는 `users`를 참조하는 FK 네 개가 전부 `NO ACTION`이라 삭제가 실패하고,
 * CASCADE로 바꾸면 여행·미션 이력이 통째로 사라지기 때문이다.
 *
 * ⚠️ **그래도 "플래그만 세우는 탈퇴"는 아니다.** 개인정보가 남으면 앱스토어 심사 요건(5.1.1(v))도
 * 개인정보 파기 의무도 만족하지 못한다. 아래를 **실제로 지운다.**
 *
 * | 지운다 | 남긴다 |
 * |---|---|
 * | 이메일·닉네임·프로필 사진 | 회원 행(익명 껍데기)·가입 시각 |
 * | 소셜 계정 행 전체(식별자·토큰) | 여행 코스·슬롯 결과 |
 * | 숙소 좌표(위치정보) | 예산·이동수단·반경 |
 * | 미션 인증 사진 주소 | 미션 완료 이력 |
 *
 * ## 아직 하지 않는 것 — 소셜 연동 해제
 * 애플은 탈퇴 시 토큰 revoke가 **심사 필수**인데, 두 가지가 없어 구현하지 못했다(9-5-4).
 *   1. 팀 ID·키 ID·`.p8` — `client_secret`을 서명해 만들어야 한다
 *   2. revoke에 넣을 토큰 — 로그인 때 `authorizationCode`를 교환해 받아 둬야 한다(명세 변경)
 *
 * 붙일 자리는 파기 **앞**이다. 소셜 토큰이 아직 살아 있는 시점이어야 하기 때문이다.
 * 다만 **연동 해제가 실패해도 탈퇴는 진행해야 한다** — 심사 요건은 "삭제가 완료되는 것"이다.
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
     * 탈퇴 처리. 이 메서드 하나가 파기 전부를 책임진다.
     *
     * `@Transactional`이 붙어 있어 **네 가지 파기가 전부 성공하거나 전부 취소된다.**
     * 중간에 실패해 "소셜 연결은 끊겼는데 이메일은 남은" 어중간한 상태가 되면,
     * 사용자는 다시 로그인할 수도 없고 개인정보는 그대로 남는다.
     *
     * 엔티티의 값을 바꾸기만 하고 `save`를 부르지 않는 것은 **더티 체킹** 덕분이다 —
     * 트랜잭션 안에서 조회한 엔티티는 JPA가 계속 지켜보다가, 트랜잭션이 끝날 때
     * 바뀐 값을 알아서 UPDATE로 내보낸다.
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
     * **순서에 의미가 있다.** 소셜 연결을 먼저 끊어야, 뒤쪽에서 예외가 나 롤백되더라도
     * "연결만 끊긴 상태"가 남지 않는다(같은 트랜잭션이라 함께 취소된다).
     *
     * 회원 번호는 `user.getId()`가 아니라 **인자로 받은 값을 쓴다.** 방금 그 번호로 찾아온
     * 회원이라 같은 값이고, 엔티티에서 다시 꺼내면 "ID가 채워져 있는가"에 의존하게 된다.
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
