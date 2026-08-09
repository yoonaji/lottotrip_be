package com.lottotrip.chat.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lottotrip.chat.entity.ChatRoom;
import com.lottotrip.chat.entity.ChatUser;
import com.lottotrip.chat.repository.ChatRoomRepository;
import com.lottotrip.chat.repository.ChatUserRepository;
import com.lottotrip.common.event.SlotDrawnEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatRoomAutoJoinListenerTest {

    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final ChatUserRepository chatUserRepository = mock(ChatUserRepository.class);
    private final ChatRoomAutoJoinListener listener =
            new ChatRoomAutoJoinListener(chatRoomRepository, chatUserRepository);

    @Test
    void 오늘_방이_없으면_새로_만들고_유저를_합류시킨다() {
        when(chatRoomRepository.findByPlaceIdAndCreatedAtBetween(any(), any(), any())).thenReturn(Optional.empty());
        ChatRoom created = ChatRoom.create(1001L, "사천진해변");
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(created);
        when(chatUserRepository.existsByRoomIdAndUserId(any(), any())).thenReturn(false);

        listener.onSlotDrawn(new SlotDrawnEvent(1L, 1001L, "사천진해변"));

        verify(chatRoomRepository, times(1)).save(any(ChatRoom.class));
        verify(chatUserRepository, times(1)).save(any(ChatUser.class));
    }

    @Test
    void 오늘_방이_이미_있으면_재사용한다() {
        ChatRoom existing = ChatRoom.create(1001L, "사천진해변");
        when(chatRoomRepository.findByPlaceIdAndCreatedAtBetween(any(), any(), any())).thenReturn(Optional.of(existing));
        when(chatUserRepository.existsByRoomIdAndUserId(any(), any())).thenReturn(false);

        listener.onSlotDrawn(new SlotDrawnEvent(1L, 1001L, "사천진해변"));

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void 이미_멤버면_중복으로_합류시키지_않는다() {
        ChatRoom existing = ChatRoom.create(1001L, "사천진해변");
        when(chatRoomRepository.findByPlaceIdAndCreatedAtBetween(any(), any(), any())).thenReturn(Optional.of(existing));
        when(chatUserRepository.existsByRoomIdAndUserId(any(), any())).thenReturn(true);

        listener.onSlotDrawn(new SlotDrawnEvent(1L, 1001L, "사천진해변"));

        verify(chatUserRepository, never()).save(any(ChatUser.class));
    }
}
