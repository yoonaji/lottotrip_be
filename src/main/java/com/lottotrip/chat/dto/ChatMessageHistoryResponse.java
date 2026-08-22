package com.lottotrip.chat.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatMessageHistoryResponse(List<MessageItem> messages, String nextCursor) {

    public record MessageItem(Long messageId, Long senderId, String nickname, String messageText, OffsetDateTime createdAt) {
    }
}
