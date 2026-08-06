package com.lottotrip.config;

import com.lottotrip.auth.jwt.JwtAuthenticationEntryPoint;
import com.lottotrip.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 시큐리티 설정 — 어떤 경로가 열려 있고 어떤 경로가 토큰을 요구하는지 정한다. (roadmap 4-2)
 *
 * <p>스프링 시큐리티는 요청이 컨트롤러에 닿기 전에 여러 개의 필터를 줄줄이 거치게 한다(필터 체인).
 * 이 클래스는 그 줄을 어떻게 세울지 적어 두는 곳이다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /** 토큰 없이 접근할 수 있는 경로. 늘어나면 여기만 고친다. */
    private static final String[] PUBLIC_PATHS = {
            // 헬스 체크 — 로드밸런서·모니터링이 토큰 없이 주기적으로 두드린다. (tour_api_erd.md 4-2)
            "/api/v1/health",
            "/actuator/health/**",
            "/actuator/health",
            // 로그인·토큰 갱신 — 토큰을 받으러 오는 곳이 토큰을 요구하면 아무도 로그인할 수 없다.
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            // API 문서
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF는 브라우저가 쿠키를 자동으로 실어 보내는 성질을 노린 공격이다.
                // 우리는 쿠키가 아니라 헤더에 토큰을 직접 담아 보내므로 해당되지 않는다.
                // 게다가 프론트가 iOS 앱이라 브라우저 쿠키 자체가 없다.
                .csrf(csrf -> csrf.disable())

                // 스프링이 기본 제공하는 로그인 폼과 브라우저 팝업 로그인을 끈다.
                // 우리는 JSON API로만 인증하므로 둘 다 쓸 일이 없고, 켜져 있으면
                // 401 대신 로그인 페이지로 리다이렉트되는 등 응답이 명세와 어긋난다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // 서버가 로그인 상태를 기억하지 않는다(무상태). 필요한 정보는 토큰 안에 다 들어 있다.
                // 세션을 쓰면 서버를 여러 대로 늘릴 때 "그 세션이 어느 서버에 있나" 문제가 생긴다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 명시하지 않은 나머지는 전부 인증 필요.
                        // 반대로("기본 허용, 필요한 것만 차단") 두면 API를 새로 추가할 때
                        // 깜빡 잊는 순간 그대로 뚫린다.
                        .anyRequest().authenticated()
                )

                // 인증 실패 시 응답을 공통 포맷으로 맞춘다.
                .exceptionHandling(handler -> handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))

                // JWT 필터를 아이디/비밀번호 필터 자리보다 앞에 끼운다.
                // 뒤쪽 필터들이 "인증됐는가"를 판단할 때 이미 결과가 준비돼 있어야 하기 때문이다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
