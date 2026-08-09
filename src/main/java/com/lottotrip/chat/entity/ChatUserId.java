package com.lottotrip.chat.entity;

import java.io.Serializable;
import java.util.Objects;

public class ChatUserId implements Serializable {

    private Long roomId;
    private Long userId;

    protected ChatUserId() {
    }

    public ChatUserId(Long roomId, Long userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatUserId that)) {
            return false;
        }
        return Objects.equals(roomId, that.roomId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
}
