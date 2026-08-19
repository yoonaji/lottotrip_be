package com.lottotrip.chat.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.lottotrip.chat.entity.ChatRoom;
import com.lottotrip.chat.entity.ChatUser;
import com.lottotrip.chat.repository.ChatRoomRepository;
import com.lottotrip.chat.repository.ChatUserRepository;
import com.lottotrip.common.event.SlotDrawnEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mockito 유닛 테스트는 Spring 프록시 없이 클래스를 직접 new 하기 때문에
 * self-invocation으로 인한 @Transactional 무시 같은 AOP 버그를 못 잡는다 (실제로 한 번 놓쳤었음).
 * 그래서 실제 스프링 컨텍스트 + 실제 트랜잭션 커밋을 태우는 통합테스트를 별도로 둔다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ChatRoomAutoJoinIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatUserRepository chatUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 슬롯_당첨_이벤트를_커밋하면_AFTER_COMMIT_리스너가_채팅방_생성과_합류를_실제로_반영한다() {
        Long placeId = 999001L;
        Long userId = 555L;

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        eventPublisher.publishEvent(new SlotDrawnEvent(userId, placeId, "통합테스트장소")));

        List<ChatUser> memberships = chatUserRepository.findByUserId(userId);
        assertThat(memberships).hasSize(1);

        ChatRoom room = chatRoomRepository.findById(memberships.get(0).getRoomId()).orElseThrow();
        assertThat(room.getPlaceId()).isEqualTo(placeId);
        assertThat(room.getTitle()).isEqualTo("통합테스트장소");
    }
}
