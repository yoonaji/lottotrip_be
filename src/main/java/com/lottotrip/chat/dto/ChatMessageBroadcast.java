package com.lottotrip.chat.dto;

import java.time.OffsetDateTime;

/** REST 이력 조회의 메시지 스키마와 동일한 형태로 맞춤. nickname은 아직 null (task #7 참고). */
public record ChatMessageBroadcast(
        Long messageId,
        Long roomId,
        Long senderId,
        String nickname,
        String messageText,
        OffsetDateTime createdAt
) {
}
