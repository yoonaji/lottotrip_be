package com.lottotrip.auth.service;

import com.lottotrip.auth.dto.WithdrawResponse;
import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.auth.entity.SocialAuth;
import com.lottotrip.auth.repository.SocialAuthRepository;
import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.enums.MediaType;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.entity.MissionStatus;
import com.lottotrip.mission.entity.UserMission;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.slot.entity.TransportType;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 회원 탈퇴 검증. (roadmap 9-5, 결정 20)
 *
 * **여기서 확인하는 것은 "무엇을 지우고 무엇을 남기는가"다.**
 * 소프트 삭제라 회원 행 자체는 남는데, **개인을 식별할 수 있는 값이 실제로 지워지지 않으면
 * 앱스토어 심사 요건(5.1.1(v))도 개인정보 파기 의무도 만족하지 못한다.**
 * "플래그만 세우는 탈퇴"가 되지 않도록 파기 대상을 하나씩 못 박는다.
 *
 * 저장소는 가짜(mock)를 쓴다. 실제 저장 동작이 아니라 **파기 범위와 순서**가 관심사이기 때문이다.
 */
class WithdrawalServiceTest {

    private static final Long USER_ID = 42L;

    private UserRepository userRepository;
    private SocialAuthRepository socialAuthRepository;
    private TripSessionRepository tripSessionRepository;
    private UserMissionRepository userMissionRepository;
    private WithdrawalService withdrawalService;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        socialAuthRepository = mock(SocialAuthRepository.class);
        tripSessionRepository = mock(TripSessionRepository.class);
        userMissionRepository = mock(UserMissionRepository.class);
        withdrawalService = new WithdrawalService(
                userRepository, socialAuthRepository, tripSessionRepository, userMissionRepository);

        user = User.create("potato@example.com", "감자러버", "https://img.kakao.com/potato.png");
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(user));
        given(tripSessionRepository.findAllByUserId(USER_ID)).willReturn(List.of());
        given(userMissionRepository.findAllByUserId(USER_ID)).willReturn(List.of());
    }

    // ---------- 파기 대상 ----------

    @Test
    @DisplayName("식별정보 3종을 실제로 지운다 — 플래그만 세우는 탈퇴가 아니다")
    void erasesIdentifyingFields() {
        withdrawalService.withdraw(USER_ID);

        assertThat(user.getEmail()).isNull();
        assertThat(user.getNickname()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("탈퇴 시각을 남긴다 — 이후 요청을 막는 기준이 된다")
    void marksDeletedAt() {
        WithdrawResponse response = withdrawalService.withdraw(USER_ID);

        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.isDeleted()).isTrue();
        assertThat(response.deleted()).isTrue();
        assertThat(response.deletedAt()).isEqualTo(user.getDeletedAt());
    }

    @Test
    @DisplayName("소셜 연결을 끊는다 — 남기면 같은 계정으로 로그인할 때 탈퇴 계정이 되살아난다")
    void deletesSocialAuth() {
        // provider_user_id는 개인 식별자이자 재로그인 열쇠다. 이것만은 반드시 지워야
        // 재로그인이 '신규 가입'으로 갈린다.
        withdrawalService.withdraw(USER_ID);

        verify(socialAuthRepository).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("숙소 좌표를 지운다 — 위치정보다")
    void erasesAccommodationLocation() {
        // 놓치기 쉬운 지점이다. 예산·이동수단·반경은 통계로 남겨도 되지만
        // 숙소 좌표는 '이 사람이 어디에 묵었는지'라 남기면 안 된다.
        TripSession session = TripSession.create(
                user, BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761);
        given(tripSessionRepository.findAllByUserId(USER_ID)).willReturn(List.of(session));

        withdrawalService.withdraw(USER_ID);

        assertThat(session.getAccommodationLatitude()).isNull();
        assertThat(session.getAccommodationLongitude()).isNull();
    }

    @Test
    @DisplayName("세션의 나머지 값은 남긴다 — 식별력이 없고 통계로 쓰인다")
    void keepsNonIdentifyingSessionFields() {
        TripSession session = TripSession.create(
                user, BudgetLevel.MEDIUM, TransportType.WALK, 37.7519, 128.8761);
        given(tripSessionRepository.findAllByUserId(USER_ID)).willReturn(List.of(session));

        withdrawalService.withdraw(USER_ID);

        assertThat(session.getBudgetRange()).isEqualTo(BudgetLevel.MEDIUM);
        assertThat(session.getTransportation()).isEqualTo(TransportType.WALK);
        assertThat(session.getSearchRadiusKm()).isEqualTo(TransportType.WALK.getSearchRadiusKm());
    }

    @Test
    @DisplayName("미션 인증 사진을 지운다 — 본인이 찍힌 사진일 수 있다")
    void erasesCertifiedMedia() {
        UserMission completed = UserMission.completeWithMedia(
                user, mission(), "https://s3.../proof.jpg", MediaType.IMAGE);
        given(userMissionRepository.findAllByUserId(USER_ID)).willReturn(List.of(completed));

        withdrawalService.withdraw(USER_ID);

        assertThat(completed.getCertifiedMediaUrl()).isNull();
    }

    @Test
    @DisplayName("미션 완료 이력 자체는 남긴다 — 식별정보가 아니다")
    void keepsMissionHistory() {
        UserMission completed = UserMission.complete(user, mission());
        given(userMissionRepository.findAllByUserId(USER_ID)).willReturn(List.of(completed));

        withdrawalService.withdraw(USER_ID);

        assertThat(completed.getStatus()).isEqualTo(MissionStatus.COMPLETED);
        verify(userMissionRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("회원 행은 지우지 않는다 — FK 4개가 가리키고 있다")
    void doesNotDeleteUserRow() {
        // users를 참조하는 FK가 전부 NO ACTION이라 삭제하면 제약 위반으로 터진다.
        // 소프트 삭제를 택한 이유이기도 하다.
        withdrawalService.withdraw(USER_ID);

        verify(userRepository, never()).delete(org.mockito.ArgumentMatchers.any(User.class));
        verify(userRepository, never()).deleteById(anyLong());
    }

    // ---------- 잘못된 요청 ----------

    @Test
    @DisplayName("이미 탈퇴한 회원이면 UNAUTHORIZED — 조회 자체가 탈퇴자를 걸러낸다")
    void rejectsAlreadyWithdrawnUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawalService.withdraw(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(socialAuthRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 UNAUTHORIZED")
    void rejectsUnknownUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawalService.withdraw(999L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ---------- 도우미 ----------

    private com.lottotrip.mission.entity.Mission mission() {
        return mock(com.lottotrip.mission.entity.Mission.class);
    }

    /** 소셜 계정이 하나 붙어 있는 상태를 흉내 낸다. */
    @SuppressWarnings("unused")
    private SocialAuth socialAuth() {
        return SocialAuth.create(user, ProviderType.KAKAO, "kakao-12345", "at", null);
    }
}
