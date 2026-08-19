package com.lottotrip.common.enums;

/**
 * 미디어 종류. ERD의 `media_type` enum에 대응한다.
 *
 * ⚠️ 잠정값이다. ERD에 값 목록이 없어 우선 정의했다.
 *
 * `place_media`(장소 사진)와 `user_missions`(미션 인증 사진) 양쪽에서 쓰인다.
 */
public enum MediaType {
    IMAGE,
    VIDEO
}
