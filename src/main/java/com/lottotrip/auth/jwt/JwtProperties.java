package com.lottotrip.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * `application.yml`의 `jwt.*` 설정을 담는 객체.
 *
 * 설정값을 쓰는 곳마다 `@Value("${jwt.secret`")}로 흩뿌리면, 키 이름이 바뀔 때
 * 어디를 고쳐야 하는지 알기 어렵다. 관련된 설정을 클래스 하나로 묶어두면 컴파일러가
 * 이름을 검사해주고, 이 클래스만 보면 JWT에 어떤 설정이 필요한지 한눈에 보인다.
 *
 * `record`로 선언하면 Spring Boot가 생성자를 통해 값을 주입한다.
 * yml의 케밥 표기(`access-token-validity-seconds`)는 자바의 카멜 표기
 * (`accessTokenValiditySeconds`)와 자동으로 짝지어진다.
 *
 * @param secret                       HS256 서명에 쓰는 비밀키. 32바이트 이상이어야 한다.
 * @param accessTokenValiditySeconds   액세스 토큰 수명(초)
 * @param refreshTokenValiditySeconds  리프레시 토큰 수명(초)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds,
        long refreshTokenValiditySeconds
) {
}
