package com.lottotrip.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lottotrip.chat.ws.StompAuthInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

/**
 * STOMP 엔드포인트 등록 검증.
 *
 * 핸드셰이크의 Origin 검사는 서블릿 컨테이너를 실제로 띄워야 겪을 수 있어(403 vs 101),
 * 여기서는 "등록할 때 Origin 허용을 걸었는가"만 본다. 실측은 카드에 남겼다 —
 * 2026-09-11, `Origin: http://localhost:3000`으로 `/ws`에 붙으면 403이었다.
 */
class WebSocketConfigTest {

    private final StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
    private final StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
    private final WebSocketConfig config = new WebSocketConfig(mock(StompAuthInterceptor.class));

    @Test
    @DisplayName("/ws 핸드셰이크는 모든 Origin을 허용한다 — 브라우저(웹 프론트)가 붙어야 한다")
    void allowsEveryOriginOnHandshake() {
        given(registry.addEndpoint("/ws")).willReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registration).setAllowedOriginPatterns("*");
    }
}
