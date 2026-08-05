package com.lottotrip.health.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 시스템 상태 점검 로직.
 *
 * <p>컨트롤러가 직접 DB를 다루지 않도록 점검 책임을 여기로 분리했다.
 * 컨트롤러는 "요청을 받아 응답을 만드는" 일만 하고, "무엇이 정상인지 판단하는" 일은 서비스가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    /** 커넥션 유효성 검사 제한 시간(초). 헬스 체크가 오래 매달려 있으면 그 자체가 장애가 된다. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 1;

    private final DataSource dataSource;

    /**
     * DB에 실제로 연결할 수 있는지 확인한다.
     *
     * <p>설정값만 읽는 것으로는 알 수 없으므로 커넥션을 하나 빌려 유효성을 확인한다.
     * 예외를 밖으로 던지지 않고 {@code false}로 바꿔 주는 이유는, 헬스 체크가 예외로 터지면
     * 500이 나가 버리기 때문이다. DB 장애는 명세상 503이어야 한다. (tour_api_erd.md 4-2)
     */
    public boolean isDatabaseUp() {
        // try-with-resources: 블록을 벗어날 때 커넥션을 자동으로 반납한다.
        // 반납하지 않으면 헬스 체크를 호출할 때마다 커넥션 풀이 조금씩 말라 간다.
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            log.warn("DB 헬스 체크 실패", e);
            return false;
        }
    }
}
