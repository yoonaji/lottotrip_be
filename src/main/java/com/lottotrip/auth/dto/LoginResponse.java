package com.lottotrip.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 로그인 응답. (tour_api_erd.md 4-1)
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {

    /**
     * @param isNewUser 이번 로그인에서 새로 가입했는지 여부. 프론트가 온보딩 화면을 띄울지 판단한다
     */
    public record UserInfo(
            Long userId,
            String nickname,
            // 명세의 키 이름은 isNewUser다. 이 표시가 없으면 boolean 관례에 따라 is가 떨어져
            // newUser로 나갈 수 있어 이름을 못 박아 둔다.
            @JsonProperty("isNewUser") boolean isNewUser
    ) {
    }
}
