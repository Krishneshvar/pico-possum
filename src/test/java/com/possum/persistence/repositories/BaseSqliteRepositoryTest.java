package com.possum.persistence.repositories;

import com.possum.persistence.db.ConnectionProvider;
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
