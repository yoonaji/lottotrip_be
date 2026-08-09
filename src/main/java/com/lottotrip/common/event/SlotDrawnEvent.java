package com.lottotrip.common.event;

/**
 * A파트(슬롯 도메인)가 saved_slots 저장에 성공하면 발행하는 이벤트.
 * B는 이 이벤트를 받아 오늘자 장소 기반 채팅방에 유저를 자동 합류시킨다.
 * placeName을 이벤트에 직접 실어 보내는 이유: B는 아직 Place 엔티티에 접근할 수단이 없어서,
 * 채팅방 조회용 장소명을 얻으려고 A의 도메인을 다시 조회하는 걸 피하기 위함.
 */
public record SlotDrawnEvent(Long userId, Long placeId, String placeName) {
}
