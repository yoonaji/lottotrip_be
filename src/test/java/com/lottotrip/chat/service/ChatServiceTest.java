package com.lottotrip.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lottotrip.chat.dto.ChatMessageBroadcast;
import com.lottotrip.chat.dto.ChatMessageHistoryResponse;
import com.lottotrip.chat.dto.ChatRoomListResponse;
import com.lottotrip.chat.entity.ChatMessage;
import com.lottotrip.chat.entity.ChatRoom;
import com.lottotrip.chat.entity.ChatUser;
import com.lottotrip.chat.repository.ChatMessageRepository;
import com.lottotrip.chat.repository.ChatRoomRepository;
import com.lottotrip.chat.repository.ChatUserRepository;
import com.lottotrip.chat.repository.RoomMemberCount;
import com.lottotrip.common.error.ApiException;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ChatServiceTest {

    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final ChatUserRepository chatUserRepository = mock(ChatUserRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ChatService chatService = new ChatService(
            chatRoomRepository, chatUserRepository, chatMessageRepository);

    @Test
    void 내가_속한_방_목록과_인원수를_반환한다() throws Exception {
        ChatRoom room = ChatRoom.create(7001L, "사천진해변");
        setId(room, "roomId", 7001L);

        when(chatUserRepository.findByUserId(1L)).thenReturn(List.of(ChatUser.of(7001L, 1L)));
        when(chatRoomRepository.findAllById(List.of(7001L))).thenReturn(List.of(room));
        RoomMemberCount count = mock(RoomMemberCount.class);
        when(count.getRoomId()).thenReturn(7001L);
        when(count.getMemberCount()).thenReturn(4L);
        when(chatUserRepository.countMembersByRoomIds(List.of(7001L))).thenReturn(List.of(count));

        ChatRoomListResponse response = chatService.getMyRooms(1L);

        assertThat(response.rooms()).hasSize(1);
        assertThat(response.rooms().get(0).roomId()).isEqualTo(7001L);
        assertThat(response.rooms().get(0).placeName()).isEqualTo("사천진해변");
        assertThat(response.rooms().get(0).memberCount()).isEqualTo(4L);
    }

    @Test
    void 참여중인_방이_없으면_빈_목록() {
        when(chatUserRepository.findByUserId(1L)).thenReturn(List.of());

        ChatRoomListResponse response = chatService.getMyRooms(1L);

        assertThat(response.rooms()).isEmpty();
    }

    @Test
    void 존재하지_않는_방의_이력_조회시_예외() {
        when(chatRoomRepository.existsById(9999L)).thenReturn(false);

        assertThatThrownBy(() -> chatService.getHistory(1L, 9999L, null, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 방_멤버가_아니면_이력_조회_예외() {
        when(chatRoomRepository.existsById(7001L)).thenReturn(true);
        when(chatUserRepository.existsByRoomIdAndUserId(7001L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> chatService.getHistory(1L, 7001L, null, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 다음_페이지가_있으면_nextCursor를_반환한다() throws Exception {
        when(chatRoomRepository.existsById(7001L)).thenReturn(true);
        when(chatUserRepository.existsByRoomIdAndUserId(7001L, 1L)).thenReturn(true);

        List<ChatMessage> threeMessages = List.of(
                buildMessage(3L, "메시지3"),
                buildMessage(2L, "메시지2"),
                buildMessage(1L, "메시지1")
        );
        when(chatMessageRepository.findByRoomIdOrderByMessageIdDesc(eq(7001L), any(PageRequest.class)))
                .thenReturn(threeMessages);

        ChatMessageHistoryResponse response = chatService.getHistory(1L, 7001L, null, 2);

        assertThat(response.messages()).hasSize(2);
        assertThat(response.messages().get(0).messageId()).isEqualTo(3L);
        assertThat(response.nextCursor()).isNotNull();
    }

    @Test
    void 메시지_발행시_저장하고_브로드캐스트용_객체를_반환한다() throws Exception {
        when(chatRoomRepository.existsById(7001L)).thenReturn(true);
        when(chatUserRepository.existsByRoomIdAndUserId(7001L, 1L)).thenReturn(true);
        ChatMessage saved = buildMessage(88012L, "안녕하세요!");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessageBroadcast broadcast = chatService.send(1L, 7001L, "안녕하세요!");

        assertThat(broadcast.messageId()).isEqualTo(88012L);
        assertThat(broadcast.roomId()).isEqualTo(7001L);
        assertThat(broadcast.senderId()).isEqualTo(1L);
        assertThat(broadcast.messageText()).isEqualTo("안녕하세요!");
    }

    @Test
    void 빈_메시지_발행시_예외() {
        assertThatThrownBy(() -> chatService.send(1L, 7001L, "  "))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 존재하지_않는_방에_발행시_예외() {
        when(chatRoomRepository.existsById(9999L)).thenReturn(false);

        assertThatThrownBy(() -> chatService.send(1L, 9999L, "안녕"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 방_멤버가_아니면_발행_예외() {
        when(chatRoomRepository.existsById(7001L)).thenReturn(true);
        when(chatUserRepository.existsByRoomIdAndUserId(7001L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> chatService.send(1L, 7001L, "안녕"))
                .isInstanceOf(ApiException.class);
    }

    private ChatMessage buildMessage(Long id, String text) throws Exception {
        ChatMessage message = ChatMessage.create(7001L, 1L, text);
        setId(message, "messageId", id);
        return message;
    }

    private void setId(Object entity, String fieldName, Long value) throws Exception {
        Field field = entity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, value);
    }
}
