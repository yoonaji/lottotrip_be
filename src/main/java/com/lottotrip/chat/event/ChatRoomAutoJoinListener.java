package com.lottotrip.chat.event;

import com.lottotrip.chat.entity.ChatRoom;
import com.lottotrip.chat.entity.ChatUser;
import com.lottotrip.chat.repository.ChatRoomRepository;
import com.lottotrip.chat.repository.ChatUserRepository;
import com.lottotrip.common.event.SlotDrawnEvent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 슬롯 당첨 시점에 "오늘의 장소 기반 채팅방"을 찾거나 만들고 유저를 자동 합류시킨다.
 * REST로 노출된 "방 참가" API는 따로 없다 — 이게 유일한 합류 경로다.
 */
@Component
public class ChatRoomAutoJoinListener {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatUserRepository chatUserRepository;

    public ChatRoomAutoJoinListener(ChatRoomRepository chatRoomRepository, ChatUserRepository chatUserRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatUserRepository = chatUserRepository;
    }

    /**
     * @Transactional을 별도 private 메서드가 아니라 반드시 이 진입 메서드에 직접 붙여야 한다 —
     * 같은 클래스 안에서 this.xxx()로 호출하면 프록시를 안 거쳐서 @Transactional이 조용히 무시된다 (self-invocation).
     * AFTER_COMMIT 시점엔 원래 트랜잭션이 이미 끝나있어서 REQUIRES_NEW로 새 트랜잭션을 시작해야 한다
     * (Spring이 @TransactionalEventListener + 기본 propagation 조합을 아예 막아버림).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotDrawn(SlotDrawnEvent event) {
        ChatRoom room = findOrCreateTodayRoom(event.placeId(), event.placeName());
        if (!chatUserRepository.existsByRoomIdAndUserId(room.getRoomId(), event.userId())) {
            chatUserRepository.save(ChatUser.of(room.getRoomId(), event.userId()));
        }
    }

    private ChatRoom findOrCreateTodayRoom(Long placeId, String placeName) {
        OffsetDateTime startOfDay = LocalDate.now(KST).atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime endOfDay = startOfDay.plusDays(1);

        return chatRoomRepository.findByPlaceIdAndCreatedAtBetween(placeId, startOfDay, endOfDay)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(placeId, placeName)));
    }
}
