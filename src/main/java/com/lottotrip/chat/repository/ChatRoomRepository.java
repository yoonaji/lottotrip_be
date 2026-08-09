package com.lottotrip.chat.repository;

import com.lottotrip.chat.entity.ChatRoom;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByPlaceIdAndCreatedAtBetween(Long placeId, OffsetDateTime from, OffsetDateTime to);
}
