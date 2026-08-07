package com.lottotrip.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 API 호출용 HTTP 클라이언트 설정.
 *
 * <p>{@code RestClient}는 스프링이 제공하는 HTTP 클라이언트다(예전의 {@code RestTemplate} 후속).
 * Spring Boot 4에서는 {@code RestClient.Builder}를 자동으로 만들어 주는 모듈이 클래스패스에 없어서
 * 여기서 직접 등록한다.
 */
@Configuration
public class RestClientConfig {

    /**
     * 연결 타임아웃 — "상대 서버와 연결이 맺어지기까지" 기다리는 시간.
     * 카카오가 응답하지 않을 때 우리 서버 스레드가 무한정 붙잡히는 것을 막는다.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * 읽기 타임아웃 — "연결된 뒤 응답이 다 올 때까지" 기다리는 시간.
     *
     * <p>타임아웃이 없으면 카카오 장애 시 로그인 요청들이 스레드를 하나씩 붙잡은 채 쌓이고,
     * 결국 <b>로그인과 무관한 API까지 전부 느려진다.</b> 한 곳의 장애가 서비스 전체로 번지는 것을
     * 막기 위해 반드시 걸어 둔다.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * {@code RestClient.Builder}를 빈으로 등록한다.
     *
     * <p>{@code prototype} 범위는 "주입할 때마다 새 객체를 만든다"는 뜻이다. builder는 값을 계속
     * 덧붙이며 상태가 바뀌는 객체라, 하나를 여러 곳이 나눠 쓰면 A가 설정한 값이 B에 묻어 들어간다.
     * (기본값인 {@code singleton}은 애플리케이션 전체가 같은 객체 하나를 공유한다.)
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(timeoutRequestFactory());
    }

    private ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
