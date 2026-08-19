package com.lottotrip.chat.dto;

import java.util.List;

public record ChatRoomListResponse(List<ChatRoomSummary> rooms) {

    public record ChatRoomSummary(Long roomId, String placeName, long memberCount) {
    }
}
