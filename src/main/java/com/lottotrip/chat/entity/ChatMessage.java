package com.lottotrip.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** 발신자 탈퇴 시 ON DELETE SET NULL 이라 nullable. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private ChatMessage(Long roomId, Long userId, String messageText) {
        this.roomId = roomId;
        this.userId = userId;
        this.messageText = messageText;
    }

    public static ChatMessage create(Long roomId, Long userId, String messageText) {
        return new ChatMessage(roomId, userId, messageText);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
