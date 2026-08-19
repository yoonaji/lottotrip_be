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

/**
 * 장소 기반 운명 공동체 채팅방. place_id + 날짜(createdAt) 조합으로 "오늘의 방"을 구분한다.
 */
@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private ChatRoom(Long placeId, String title) {
        this.placeId = placeId;
        this.title = title;
    }

    public static ChatRoom create(Long placeId, String title) {
        return new ChatRoom(placeId, title);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
