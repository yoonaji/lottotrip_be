package com.lottotrip.chat.service;

import com.lottotrip.chat.dto.ChatMessageBroadcast;
import com.lottotrip.chat.dto.ChatMessageHistoryResponse;
import com.lottotrip.chat.dto.ChatMessageHistoryResponse.MessageItem;
import com.lottotrip.chat.dto.ChatRoomListResponse;
import com.lottotrip.chat.dto.ChatRoomListResponse.ChatRoomSummary;
import com.lottotrip.chat.entity.ChatMessage;
import com.lottotrip.chat.entity.ChatRoom;
import com.lottotrip.chat.entity.ChatUser;
import com.lottotrip.chat.repository.ChatMessageRepository;
import com.lottotrip.chat.repository.ChatRoomRepository;
import com.lottotrip.chat.repository.ChatUserRepository;
import com.lottotrip.chat.repository.RoomMemberCount;
import com.lottotrip.common.error.ApiException;
import com.lottotrip.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatService {

    private static final int DEFAULT_HISTORY_SIZE = 20;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatUserRepository chatUserRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ChatRoomRepository chatRoomRepository, ChatUserRepository chatUserRepository,
                        ChatMessageRepository chatMessageRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatUserRepository = chatUserRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public ChatRoomListResponse getMyRooms(Long userId) {
        List<ChatUser> memberships = chatUserRepository.findByUserId(userId);
        List<Long> roomIds = memberships.stream().map(ChatUser::getRoomId).toList();
        if (roomIds.isEmpty()) {
            return new ChatRoomListResponse(List.of());
        }

        Map<Long, ChatRoom> roomsById = chatRoomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(ChatRoom::getRoomId, Function.identity()));
        Map<Long, Long> memberCounts = chatUserRepository.countMembersByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(RoomMemberCount::getRoomId, RoomMemberCount::getMemberCount));

        List<ChatRoomSummary> summaries = roomsById.values().stream()
                .sorted(Comparator.comparing(ChatRoom::getCreatedAt).reversed())
                .map(room -> new ChatRoomSummary(
                        room.getRoomId(),
                        room.getTitle(),
                        memberCounts.getOrDefault(room.getRoomId(), 0L)
                ))
                .toList();

        return new ChatRoomListResponse(summaries);
    }

    @Transactional(readOnly = true)
    public ChatMessageHistoryResponse getHistory(Long userId, Long roomId, String cursor, Integer size) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new ApiException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (!chatUserRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ApiException(ErrorCode.NOT_ROOM_MEMBER);
        }

        int pageSize = (size != null && size > 0) ? size : DEFAULT_HISTORY_SIZE;
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

        List<ChatMessage> fetched = (cursor != null)
                ? chatMessageRepository.findByRoomIdAndMessageIdLessThanOrderByMessageIdDesc(roomId, decodeCursor(cursor), pageRequest)
                : chatMessageRepository.findByRoomIdOrderByMessageIdDesc(roomId, pageRequest);

        boolean hasNext = fetched.size() > pageSize;
        List<ChatMessage> page = hasNext ? fetched.subList(0, pageSize) : fetched;

        List<MessageItem> messages = page.stream()
                .map(message -> new MessageItem(
                        message.getMessageId(),
                        message.getUserId(),
                        null, // TODO(A 연동): 닉네임 조회 방법 확정되면 채우기
                        message.getMessageText(),
                        message.getCreatedAt()
                ))
                .toList();

        String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1).getMessageId()) : null;
        return new ChatMessageHistoryResponse(messages, nextCursor);
    }

    @Transactional
    public ChatMessageBroadcast send(Long userId, Long roomId, String messageText) {
        if (!StringUtils.hasText(messageText)) {
            throw new ApiException(ErrorCode.BAD_REQUEST);
        }
        if (!chatRoomRepository.existsById(roomId)) {
            throw new ApiException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (!chatUserRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ApiException(ErrorCode.NOT_ROOM_MEMBER);
        }

        ChatMessage saved = chatMessageRepository.save(ChatMessage.create(roomId, userId, messageText));

        return new ChatMessageBroadcast(
                saved.getMessageId(),
                roomId,
                userId,
                null, // TODO(A 연동): 닉네임 조회 방법 확정되면 채우기
                saved.getMessageText(),
                saved.getCreatedAt()
        );
    }

    private String encodeCursor(Long lastMessageId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(lastMessageId).getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            return Long.valueOf(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST);
        }
    }
}
