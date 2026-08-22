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
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    public ChatService(ChatRoomRepository chatRoomRepository, ChatUserRepository chatUserRepository,
                        ChatMessageRepository chatMessageRepository, UserRepository userRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatUserRepository = chatUserRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
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
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (!chatUserRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }

        int pageSize = (size != null && size > 0) ? size : DEFAULT_HISTORY_SIZE;
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

        List<ChatMessage> fetched = (cursor != null)
                ? chatMessageRepository.findByRoomIdAndMessageIdLessThanOrderByMessageIdDesc(roomId, decodeCursor(cursor), pageRequest)
                : chatMessageRepository.findByRoomIdOrderByMessageIdDesc(roomId, pageRequest);

        boolean hasNext = fetched.size() > pageSize;
        List<ChatMessage> page = hasNext ? fetched.subList(0, pageSize) : fetched;

        Map<Long, String> nicknamesBySenderId = nicknamesOf(page.stream().map(ChatMessage::getUserId).toList());

        List<MessageItem> messages = page.stream()
                .map(message -> new MessageItem(
                        message.getMessageId(),
                        message.getUserId(),
                        nicknamesBySenderId.get(message.getUserId()),
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
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        if (!chatRoomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (!chatUserRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }

        ChatMessage saved = chatMessageRepository.save(ChatMessage.create(roomId, userId, messageText));

        return new ChatMessageBroadcast(
                saved.getMessageId(),
                roomId,
                userId,
                nicknameOf(userId),
                saved.getMessageText(),
                saved.getCreatedAt()
        );
    }

    /**
     * 탈퇴 여부와 무관하게 닉네임을 그대로 보여준다 — 과거 채팅 이력에서 탈퇴한 사람 발언을
     * "알 수 없음"으로 지울 이유가 없다. 이미 인증(JWT/STOMP)에서 탈퇴 여부는 걸러진 뒤다.
     */
    private String nicknameOf(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse(null);
    }

    private Map<Long, String> nicknamesOf(List<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
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
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }
}
