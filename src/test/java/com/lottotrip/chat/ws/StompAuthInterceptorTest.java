package com.lottotrip.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class StompAuthInterceptorTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthInterceptor(jwtProvider, userRepository);
    }

    @Test
    void 유효한_토큰이면_유저를_설정한다() {
        given(jwtProvider.getUserIdFromAccessToken("valid-token")).willReturn(1L);
        given(userRepository.existsByIdAndDeletedAtIsNull(1L)).willReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, null);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("1");
    }

    @Test
    void Authorization_헤더가_없으면_예외() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void 토큰_검증에_실패하면_예외() {
        given(jwtProvider.getUserIdFromAccessToken("bad-token"))
                .willThrow(new CustomException(ErrorCode.UNAUTHORIZED));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void 탈퇴한_회원의_토큰이면_예외() {
        given(jwtProvider.getUserIdFromAccessToken("valid-token")).willReturn(1L);
        given(userRepository.existsByIdAndDeletedAtIsNull(1L)).willReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void CONNECT가_아닌_프레임은_그냥_통과시킨다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }
}
