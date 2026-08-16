package com.lottotrip.auth.jwt;

import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청이 보호된 경로에 들어왔을 때의 401 응답을 만든다. (roadmap 4-2)
 *
 * 왜 따로 필요한가. 시큐리티가 막은 요청은 컨트롤러까지 가지 않는다.
 * 그래서 `@RestControllerAdvice`인 `GlobalExceptionHandler`가 손댈 수 없고,
 * 그대로 두면 스프링 기본 형식으로 응답이 나간다. 프론트 입장에서는 같은 서버가
 * 두 가지 에러 포맷을 내려주는 셈이라, 디코딩 코드를 두 벌 만들어야 한다.
 *
 * 그래서 이 지점에서 응답 본문을 직접 써서 공통 포맷(`success`/`data`/`error`)을
 * 맞춰 준다. (tour_api_erd.md 3-1)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * 스프링 부트가 이미 만들어 둔 JSON 변환기를 그대로 주입받는다. 직렬화 설정이 컨트롤러 응답과 같아진다.
     *
     * 패키지가 `tools.jackson`인 점에 주의. Spring Boot 4는 Jackson 3을 쓰는데,
     * Jackson 2(`com.fasterxml.jackson`)와 패키지 이름이 아예 다르다.
     * 습관대로 `com.fasterxml`을 import하면 컴파일은 되지만
     * "그런 빈이 없다"며 서버가 뜨지 않는다. 클래스명이 같아 원인을 찾기 어렵다.
     */
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
