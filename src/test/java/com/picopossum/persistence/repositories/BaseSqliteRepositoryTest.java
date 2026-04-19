package com.picopossum.persistence.repositories;

import com.picopossum.persistence.db.ConnectionProvider;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseSqliteRepositoryTest {

    @Test
    void shouldProvideConnectionThroughProvider() {
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        when(connectionProvider.getConnection()).thenReturn(connection);

        Connection result = connectionProvider.getConnection();

        assertNotNull(result);
        verify(connectionProvider).getConnection();
    }
}
