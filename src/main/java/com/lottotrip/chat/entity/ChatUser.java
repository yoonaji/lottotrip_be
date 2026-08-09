package com.lottotrip.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_users")
@IdClass(ChatUserId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatUser {

    @Id
    @Column(name = "room_id")
    private Long roomId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    private ChatUser(Long roomId, Long userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    public static ChatUser of(Long roomId, Long userId) {
        return new ChatUser(roomId, userId);
    }

    @PrePersist
    void prePersist() {
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
    }
}
