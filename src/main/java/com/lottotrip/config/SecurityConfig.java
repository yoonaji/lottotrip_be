package com.lottotrip.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final ObjectProvider<DevHeaderAuthFilter> devHeaderAuthFilter;

    public SecurityConfig(ObjectProvider<DevHeaderAuthFilter> devHeaderAuthFilter) {
        this.devHeaderAuthFilter = devHeaderAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(CsrfConfigurer::disable) // JWT 기반 stateless API라 세션/쿠키 CSRF 방어가 필요 없음
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/ws/**").permitAll() // STOMP는 CONNECT 프레임에서 자체 인증
                        .anyRequest().authenticated()
                );
        // TODO(A): 실제 JWT 인증 필터로 교체되면 이 줄과 DevHeaderAuthFilter는 제거
        devHeaderAuthFilter.ifAvailable(filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));
        return http.build();
    }
}
