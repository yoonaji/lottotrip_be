package com.lottotrip.chat.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatMessageHistoryResponse(List<MessageItem> messages, String nextCursor) {

    /**
     * nickname은 아직 null로 내려간다 — users 테이블/닉네임 조회 방법이 A파트에서 확정되면 채워야 함 (task #7).
     */
    public record MessageItem(Long messageId, Long senderId, String nickname, String messageText, OffsetDateTime createdAt) {
    }
}
