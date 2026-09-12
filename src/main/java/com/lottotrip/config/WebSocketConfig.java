package com.lottotrip.config;

import com.lottotrip.chat.ws.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;

    public WebSocketConfig(StompAuthInterceptor stompAuthInterceptor) {
        this.stompAuthInterceptor = stompAuthInterceptor;
    }

    /**
     * STOMP 핸드셰이크 엔드포인트. 순수 WebSocket이며 SockJS는 쓰지 않는다.
     *
     * Origin은 전부 허용한다. Spring 기본은 same-origin만 허용해서 브라우저가 다른 출처
     * (`localhost:3000`, 웹 배포 도메인)에서 붙으면 핸드셰이크가 403으로 끊긴다.
     * iOS 앱은 Origin 헤더를 보내지 않아 지금까지 걸리지 않았고, 웹 프론트를 붙이면서
     * 드러났다 (2026-09-11 실측: Origin 없음 → 101, `http://localhost:3000` → 403).
     *
     * REST는 웹이 자기 서버(Next.js 프록시)를 거쳐 서버 대 서버로 부르므로 CORS가 없지만,
     * WebSocket은 그렇게 중계할 수 없어 브라우저가 여기에 직접 붙어야 한다.
     * 열어 두어도 인증은 그대로다 — CONNECT 프레임의 JWT를 {@link StompAuthInterceptor}가 검사한다.
     * 웹 배포 도메인이 정해지면 `"*"` 대신 도메인을 나열해도 된다.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
