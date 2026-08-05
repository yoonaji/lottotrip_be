package com.lottotrip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                // 헬스 체크는 인증 없이 호출할 수 있어야 한다. (tour_api_erd.md 4-2 "인증 불필요")
                // 로드밸런서·모니터링이 토큰 없이 주기적으로 두드리는 엔드포인트이기 때문이다.
                .requestMatchers("/api/v1/health").permitAll()
                .anyRequest().authenticated()
        );
        return http.build();
    }
}
