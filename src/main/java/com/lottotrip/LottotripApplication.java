package com.lottotrip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * `@ConfigurationPropertiesScan` — `@ConfigurationProperties`가 붙은 클래스를
 * 찾아 설정값을 채워 빈으로 등록한다. 이게 없으면 `JwtProperties`는 그냥 평범한
 * 클래스일 뿐이라 주입받을 수 없다.
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class LottotripApplication {

    public static void main(String[] args) {
        SpringApplication.run(LottotripApplication.class, args);
    }
}
