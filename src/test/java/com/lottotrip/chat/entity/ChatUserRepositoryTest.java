package com.lottotrip.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.lottotrip.chat.repository.ChatRoomRepository;
import com.lottotrip.chat.repository.ChatUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ChatUserRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatUserRepository chatUserRepository;

    @Test
    void 복합키로_방_멤버를_저장하고_조회한다() {
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(1001L, "사천진해변 운명 공동체"));

        chatUserRepository.save(ChatUser.of(room.getRoomId(), 1L));
        chatUserRepository.save(ChatUser.of(room.getRoomId(), 2L));

        assertThat(chatUserRepository.countByRoomId(room.getRoomId())).isEqualTo(2);
        assertThat(chatUserRepository.existsByRoomIdAndUserId(room.getRoomId(), 1L)).isTrue();
        assertThat(chatUserRepository.existsByRoomIdAndUserId(room.getRoomId(), 999L)).isFalse();
    }

    @Test
    void 같은_유저가_여러_방에_속할_수_있다() {
        ChatRoom roomA = chatRoomRepository.save(ChatRoom.create(1001L, "방A"));
        ChatRoom roomB = chatRoomRepository.save(ChatRoom.create(1002L, "방B"));

        chatUserRepository.save(ChatUser.of(roomA.getRoomId(), 1L));
        chatUserRepository.save(ChatUser.of(roomB.getRoomId(), 1L));

        assertThat(chatUserRepository.findByUserId(1L)).hasSize(2);
    }
}
