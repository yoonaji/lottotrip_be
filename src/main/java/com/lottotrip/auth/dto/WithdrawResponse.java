package com.lottotrip.auth.dto;

import java.time.LocalDateTime;

/**
 * 회원 탈퇴 응답. (tour_api_erd.md 4-1, roadmap 9-5)
 *
 * `deleted`를 담는 이유: 200인데 본문이 비어 있으면 프론트가 "정말 처리됐나"를 다시 확인하게 된다.
 *
 * @param deleted   항상 `true`다. 실패는 예외로 나가 에러 응답이 된다
 * @param deletedAt 탈퇴 처리 시각. 앱이 "탈퇴 완료" 화면에 쓰거나 로그로 남길 수 있다
 */
public record WithdrawResponse(boolean deleted, LocalDateTime deletedAt) {

    public static WithdrawResponse of(LocalDateTime deletedAt) {
        return new WithdrawResponse(true, deletedAt);
    }
}
