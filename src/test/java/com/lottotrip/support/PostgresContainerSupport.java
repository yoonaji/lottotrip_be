package com.lottotrip.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DB가 필요한 테스트가 함께 쓰는 PostgreSQL 컨테이너.
 *
 * <p>테스트 클래스마다 컨테이너를 따로 띄우면 클래스 수만큼 수십 초씩 늘어난다.
 * {@code static} 필드에 하나만 만들고 상속해서 나눠 쓰면 JVM 전체에서 한 번만 뜬다.
 *
 * <p>직접 {@code stop()}하지 않는 이유: Testcontainers가 함께 띄우는 정리용 컨테이너(Ryuk)가
 * 테스트 JVM이 끝나는 것을 감지해 알아서 지운다.
 */
public abstract class PostgresContainerSupport {

    /** {@code @ServiceConnection}이 컨테이너의 주소·계정을 스프링 DataSource 설정에 자동으로 꽂아 준다. */
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
