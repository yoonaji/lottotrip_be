package com.lottotrip.mission.entity;

/**
 * 미션 수행 상태. ERD의 `mission_status` enum에 대응한다.
 *
 * ⚠️ 잠정값이다. ERD에 값 목록이 없어 우선 정의했다.
 *
 * 현재 명세에는 미션 완료 API 하나뿐이고 `user_missions`는 완료 시점에 INSERT되므로,
 * 실제로 저장되는 값은 {@link #COMPLETED} 하나다.
 */
public enum MissionStatus {
    IN_PROGRESS,
    COMPLETED
}
