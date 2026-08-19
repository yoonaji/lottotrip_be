package com.lottotrip.chat.repository;

import com.lottotrip.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByMessageIdDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndMessageIdLessThanOrderByMessageIdDesc(Long roomId, Long messageId, Pageable pageable);
}
