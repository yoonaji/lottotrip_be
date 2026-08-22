package com.lottotrip.chat.ws;

import com.lottotrip.auth.jwt.JwtProvider;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.user.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * CONNECT 프레임의 Authorization 헤더로 인증한다.
 * HTTP 쪽 {@code JwtAuthenticationFilter}와 동일하게 실제 액세스 토큰을 검증하고,
 * 탈퇴한 회원의 토큰도 여기서 끊는다.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public StompAuthInterceptor(JwtProvider jwtProvider, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                throw new MessagingException("인증이 필요합니다.");
            }

            String token = authHeader.substring(BEARER_PREFIX.length());
            Long userId;
            try {
                userId = jwtProvider.getUserIdFromAccessToken(token);
            } catch (CustomException e) {
                throw new MessagingException("인증이 필요합니다.", e);
            }

            if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
                throw new MessagingException("인증이 필요합니다.");
            }

            accessor.setUser(new StompPrincipal(String.valueOf(userId)));
        }
        return message;
    }
}
