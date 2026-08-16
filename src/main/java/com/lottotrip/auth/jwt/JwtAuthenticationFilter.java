package com.lottotrip.auth.jwt;

import com.lottotrip.common.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 요청 헤더의 JWT를 읽어 "이 요청은 누가 보냈는가"를 시큐리티에 알려주는 필터. (roadmap 4-2)
 *
 * ## 필터가 무엇인가
 * 요청이 컨트롤러에 닿기 전에 반드시 거치는 관문이다. 모든 API가 각자
 * "토큰 좀 까볼게요"를 반복하지 않도록, 공통으로 한 번만 처리하는 자리다.
 *
 * {@link OncePerRequestFilter}를 상속하는 이유는 이름 그대로다. 서블릿 컨테이너는
 * 내부 forward 같은 상황에서 같은 요청에 필터를 여러 번 태울 수 있는데,
 * 이 부모 클래스가 "한 요청당 한 번"을 보장해 준다.
 *
 * ## 토큰이 잘못됐을 때 여기서 막지 않는 이유
 * 이 필터는 판단만 하고 차단은 하지 않는다. 인증 정보를 등록하지 않은 채 그냥 넘기면,
 * 뒤에 있는 시큐리티가 "이 경로는 인증이 필요한데 인증 정보가 없네" 하고 401을 낸다.
 * 역할을 이렇게 나눠 두면 인증이 필요 없는 경로(헬스 체크 등)에 낡은 토큰이 딸려 와도
 * 요청이 정상 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        resolveToken(request).ifPresent(token -> authenticate(token, request));
        filterChain.doFilter(request, response);
    }

    /**`Authorization: Bearer xxx` 형태에서 토큰만 떼어 낸다.*/
    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /**
     * 토큰이 유효하면 인증 정보를 {@link SecurityContextHolder}에 넣는다.
     *
     * SecurityContextHolder는 "지금 이 요청을 처리 중인 사람"을 담아 두는 보관함이다.
     * 스레드마다 따로 존재해서(ThreadLocal), 동시에 들어온 다른 요청과 섞이지 않는다.
     * 컨트롤러는 나중에 `@AuthenticationPrincipal`로 여기 담긴 userId를 꺼내 쓴다.
     */
    private void authenticate(String token, HttpServletRequest request) {
        try {
            Long userId = jwtProvider.getUserIdFromAccessToken(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (CustomException e) {
            // 여기서 예외를 밖으로 던지면 GlobalExceptionHandler가 잡지 못한다.
            // 필터는 컨트롤러보다 앞단이라 @RestControllerAdvice의 관할 밖이기 때문이다.
            // 인증하지 않은 채 넘기면 시큐리티가 알아서 401을 만들어 준다.
            log.debug("JWT 인증 실패 ({} {}): {}", request.getMethod(), request.getRequestURI(),
                    e.getErrorCode().getCode());
        }
    }
}
