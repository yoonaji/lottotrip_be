package com.lottotrip.chat.ws;

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
 * TODO(A): 지금은 JWT가 없어서 "Bearer {token}"의 token 값을 그대로 userId로 취급하는 임시 구현.
 * 실제 JWT 인증이 붙으면 이 클래스에서 토큰 검증 + userId claim 추출로 교체하면 되고,
 * 헤더 이름/형식(Authorization: Bearer ...)은 그대로 유지되므로 클라이언트 쪽 변경은 없다.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                throw new MessagingException("인증이 필요합니다.");
            }
            String userId = authHeader.substring(BEARER_PREFIX.length());
            accessor.setUser(new StompPrincipal(userId));
        }
        return message;
    }
}
