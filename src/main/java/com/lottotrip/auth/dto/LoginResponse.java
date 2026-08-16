package com.lottotrip.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 로그인 응답. (tour_api_erd.md 4-1)
 *
 * Entity(`User`)를 그대로 내려보내지 않고 이 DTO를 따로 두는 이유는 두 가지다.
 *   - Entity에는 내보내면 안 되는 값이 섞여 있다. 지금은 없더라도 나중에 컬럼이 하나 추가되면
 *       **아무도 의도하지 않은 채** API 응답에 딸려 나간다.
 *   - DB 컬럼명이 바뀔 때마다 API 응답이 함께 바뀌면 프론트가 그때마다 깨진다.
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
