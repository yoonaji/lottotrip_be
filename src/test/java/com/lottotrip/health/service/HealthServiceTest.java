package com.lottotrip.health.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HealthServiceTest {

    @Test
    @DisplayName("커넥션이 유효하면 DB 상태를 정상으로 판단한다")
    void isDatabaseUp_returnsTrue_whenConnectionIsValid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(true);

        assertThat(new HealthService(dataSource).isDatabaseUp()).isTrue();
    }

    @Test
    @DisplayName("커넥션이 유효하지 않으면 DB 상태를 비정상으로 판단한다")
    void isDatabaseUp_returnsFalse_whenConnectionIsInvalid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(false);

        assertThat(new HealthService(dataSource).isDatabaseUp()).isFalse();
    }

    @Test
    @DisplayName("커넥션을 얻지 못하면 예외를 밖으로 던지지 않고 비정상으로 판단한다")
    void isDatabaseUp_returnsFalse_whenConnectionFails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        given(dataSource.getConnection()).willThrow(new SQLException("연결 실패"));

        // 헬스 체크가 예외로 터지면 500이 나가 버린다. 명세상 DB 장애는 503이어야 하므로
        // 여기서 예외를 삼키고 boolean으로 바꿔 준다.
        assertThat(new HealthService(dataSource).isDatabaseUp()).isFalse();
    }

    @Test
    @DisplayName("점검이 끝나면 커넥션을 반드시 반납한다")
    void isDatabaseUp_closesConnection() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(true);

        new HealthService(dataSource).isDatabaseUp();

        // 반납하지 않으면 헬스 체크를 호출할 때마다 커넥션 풀이 말라 죽는다.
        verify(connection).close();
    }
}
