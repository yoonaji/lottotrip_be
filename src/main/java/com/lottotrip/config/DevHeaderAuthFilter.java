package com.lottotrip.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A파트의 JWT 인증 필터가 준비되기 전까지 로컬/도커 환경에서만 쓰는 임시 인증 통로.
 * "X-User-Id" 헤더 값을 그대로 인증된 유저로 취급한다. JWT 필터가 붙으면 이 클래스는 삭제한다.
 */
@Component
@Profile({"local", "docker"})
public class DevHeaderAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = request.getHeader(HEADER);
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
