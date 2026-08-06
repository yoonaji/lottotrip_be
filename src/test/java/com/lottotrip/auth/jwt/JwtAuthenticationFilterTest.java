package com.lottotrip.auth.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * JWT 인증 필터 검증. (roadmap 4-2)
 *
 * <p>필터는 컨트롤러에 도달하기 전에 요청을 가로채는 관문이다. 여기서 하는 일은 두 가지뿐이다.
 * <ol>
 *   <li>{@code Authorization} 헤더의 토큰을 꺼내 검증한다.</li>
 *   <li>통과하면 "이 요청은 N번 회원의 것"이라고 시큐리티에 등록한다.</li>
 * </ol>
 *
 * <p><b>토큰이 잘못돼도 필터는 예외를 던지지 않는다.</b> 그냥 인증되지 않은 상태로 흘려보낸다.
 * 막는 일은 시큐리티가 뒤에서 하고, 필터는 판단만 한다. 이렇게 나눠두면
 * "인증이 필요 없는 경로"에 잘못된 토큰이 딸려 와도 요청이 정상 처리된다.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-only-secret-key-for-jwt-provider-32bytes-over";

    private final JwtProvider jwtProvider =
            new JwtProvider(new JwtProperties(SECRET, 3600L, 1_209_600L));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

    @AfterEach
    void clearContext() {
        // SecurityContext는 스레드에 붙어 있어(ThreadLocal) 테스트 간에 그대로 남는다.
        // 지우지 않으면 앞 테스트의 인증 정보가 뒤 테스트를 통과시켜 버린다.
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 액세스 토큰이 오면 해당 userId로 인증 정보를 등록한다")
    void authenticates_withValidAccessToken() throws Exception {
        MockHttpServletRequest request = requestWithHeader("Bearer " + jwtProvider.createAccessToken(7L));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(7L);
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("인증에 성공해도 요청은 그대로 다음 단계로 넘어간다")
    void passesRequestAlong_afterAuthentication() throws Exception {
        MockHttpServletRequest request = requestWithHeader("Bearer " + jwtProvider.createAccessToken(7L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // 필터는 통과 여부와 무관하게 항상 다음으로 넘긴다. 여기서 응답을 직접 쓰지 않는다.
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증하지 않고 그냥 통과시킨다")
    void doesNotAuthenticate_withoutHeader() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer not-a-jwt",
            "Bearer ",
            "Basic dXNlcjpwYXNz",   // 다른 인증 방식
            "eyJhbGciOiJIUzI1NiJ9", // Bearer 접두사 없음
    })
    @DisplayName("토큰이 잘못됐거나 형식이 다르면 인증하지 않는다")
    void doesNotAuthenticate_withUnusableHeader(String headerValue) throws Exception {
        filter.doFilter(requestWithHeader(headerValue), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("잘못된 토큰이 와도 필터는 예외를 던지지 않고 요청을 넘긴다")
    void doesNotThrow_withInvalidToken() throws Exception {
        // 필터에서 예외가 터지면 @RestControllerAdvice가 잡지 못한다.
        // 필터는 컨트롤러보다 앞단이라 예외 처리기의 관할 밖이기 때문이다.
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = requestWithHeader("Bearer broken.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("만료된 토큰으로는 인증하지 않는다")
    void doesNotAuthenticate_withExpiredToken() throws Exception {
        String expired = new JwtProvider(new JwtProperties(SECRET, -60L, -60L)).createAccessToken(7L);

        filter.doFilter(requestWithHeader("Bearer " + expired), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("리프레시 토큰으로는 API 인증을 통과할 수 없다")
    void doesNotAuthenticate_withRefreshToken() throws Exception {
        // 리프레시 토큰은 토큰 갱신 API에서만 쓰인다. 수명이 길어 일반 API에 쓰이면 위험하다.
        String refresh = jwtProvider.createRefreshToken(7L);

        filter.doFilter(requestWithHeader("Bearer " + refresh), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두사의 대소문자가 달라도 인식한다")
    void acceptsBearerPrefixCaseInsensitively() throws Exception {
        // RFC 6750에서 인증 스킴 이름은 대소문자를 구분하지 않는다.
        MockHttpServletRequest request = requestWithHeader("bearer " + jwtProvider.createAccessToken(7L));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private MockHttpServletRequest requestWithHeader(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }
}
