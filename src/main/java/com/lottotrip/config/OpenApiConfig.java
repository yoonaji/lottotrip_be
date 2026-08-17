package com.lottotrip.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정. (roadmap 9-3)
 *
 * springdoc은 `@RestController`를 스스로 훑어 문서를 만든다. 이 클래스가 하는 일은
 * 자동으로 알 수 없는 두 가지를 알려 주는 것뿐이다 — 문서 제목과 인증 방식.
 *
 * 화면 주소는 `/swagger-ui.html`이고 원본 JSON은 `/v3/api-docs`다.
 * 둘 다 `SecurityConfig`의 PUBLIC_PATHS에 열려 있다.
 */
@Configuration
public class OpenApiConfig {

    /** 화면 우측 상단 Authorize 버튼에 붙는 이름. 아래 두 곳에서 같은 값을 써야 이어진다. */
    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * 문서 전체 설정.
     *
     * `securityRequirement`를 전역으로 걸면 모든 API에 자물쇠가 붙는다. 로그인·헬스 체크처럼
     * 인증이 없는 API에도 붙지만, 토큰 없이도 호출은 되므로 실사용에 문제가 없다.
     * 반대로 걸지 않으면 Authorize 버튼을 눌러도 토큰이 요청에 실리지 않아
     * 인증이 필요한 API를 화면에서 시험해 볼 수 없다.
     */
    @Bean
    public OpenAPI lottotripOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, jwtScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("로또 트립 API (개발자 A)")
                .version("v1")
                .description("""
                        슬롯머신을 돌려 랜덤 여행지와 미션을 발급받는 서비스의 백엔드.

                        담당 범위: 인증 · 시스템 · 슬롯 · 코스 · 미션
                        (영상·채팅은 개발자 B 담당이며 병합 후 이 문서에 함께 나타난다)

                        인증: 우측 상단 Authorize에 로그인으로 받은 accessToken을 넣으면
                        자물쇠가 붙은 API를 여기서 바로 호출해 볼 수 있다. 'Bearer '는 빼고 토큰만 넣는다.

                        응답은 모두 { success, data, error } 형태다. 실패 시 error.code는
                        enum 이름이 아니라 코드 문자열(COMMON_401 등)이다.
                        """);
    }

    /**
     * JWT 인증 방식 정의.
     *
     * `bearerFormat`은 화면에 표시되는 설명일 뿐 검증에 쓰이지 않는다.
     * 실제 검증은 `JwtAuthenticationFilter`가 한다.
     */
    private SecurityScheme jwtScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인 응답의 accessToken. 수명 1시간");
    }
}
