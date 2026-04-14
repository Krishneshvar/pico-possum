package com.possum.persistence.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionManagerTest {

    private ConnectionProvider connectionProvider;
    private Connection connection;
    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() throws SQLException {
        connectionProvider = mock(ConnectionProvider.class);
        connection = mock(Connection.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        transactionManager = new TransactionManager(connectionProvider);
    }

    @Test
    void shouldRejectNullConnectionProvider() {
        assertThrows(NullPointerException.class, () -> new TransactionManager(null));
    }

    @Test
    void shouldRejectNullBlock() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        assertThrows(NullPointerException.class, () -> transactionManager.runInTransaction(null));
    }

    @Test
    void shouldCommitSuccessfulOutermostTransaction() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);

        String result = transactionManager.runInTransaction(() -> "success");

        assertEquals("success", result);
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
        verify(connection, never()).rollback();
    }

    @Test
    void shouldRollbackFailedOutermostTransaction() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(() -> {
                    throw new RuntimeException("failure");
                })
        );

        assertEquals("failure", exception.getMessage());
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection, never()).commit();
    }

    @Test
    void shouldHandleNestedTransactionWithSavepoint() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(false);
        Savepoint savepoint = mock(Savepoint.class);
        when(connection.setSavepoint()).thenReturn(savepoint);

        String result = transactionManager.runInTransaction(() -> "nested");

        assertEquals("nested", result);
        verify(connection).setSavepoint();
        verify(connection).releaseSavepoint(savepoint);
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void shouldRollbackNestedTransactionToSavepoint() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(false);
        Savepoint savepoint = mock(Savepoint.class);
        when(connection.setSavepoint()).thenReturn(savepoint);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(() -> {
                    throw new RuntimeException("nested failure");
                })
        );

        assertEquals("nested failure", exception.getMessage());
        verify(connection).setSavepoint();
        verify(connection).rollback(savepoint);
        verify(connection, atLeastOnce()).releaseSavepoint(savepoint);
    }

    @Test
    void shouldWrapSQLExceptionOnCommitFailure() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("commit failed")).when(connection).commit();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                transactionManager.runInTransaction(() -> "data")
        );

        assertTrue(exception.getMessage().contains("Transaction commit failed"));
        verify(connection).setAutoCommit(false);
        verify(connection).setAutoCommit(true);
    }

    @Test
    void shouldSuppressRollbackExceptionWhenPrimaryFailureExists() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("rollback failed")).when(connection).rollback();

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(() -> {
                    throw new RuntimeException("primary failure");
                })
        );

        assertEquals("primary failure", exception.getMessage());
        assertEquals(1, exception.getSuppressed().length);
        assertTrue(exception.getSuppressed()[0] instanceof SQLException);
    }

    @Test
    void shouldSuppressAutoCommitRestoreExceptionWhenPrimaryFailureExists() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("restore failed")).when(connection).setAutoCommit(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(() -> {
                    throw new RuntimeException("primary failure");
                })
        );

        assertEquals("primary failure", exception.getMessage());
        assertTrue(exception.getSuppressed().length > 0);
    }

    @Test
    void shouldThrowIllegalStateWhenAutoCommitRestoreFailsWithoutPrimaryFailure() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("restore failed")).when(connection).setAutoCommit(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                transactionManager.runInTransaction(() -> "success")
        );

        assertTrue(exception.getMessage().contains("Failed to restore auto-commit mode"));
    }

    @Test
    void shouldThrowIllegalStateWhenGetAutoCommitFails() throws SQLException {
        when(connection.getAutoCommit()).thenThrow(new SQLException("getAutoCommit failed"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                transactionManager.runInTransaction(() -> "data")
        );

        assertTrue(exception.getMessage().contains("Unable to read auto-commit state"));
    }

    @Test
    void shouldThrowIllegalStateWhenSavepointCreationFails() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.setSavepoint()).thenThrow(new SQLException("savepoint failed"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                transactionManager.runInTransaction(() -> "data")
        );

        assertTrue(exception.getMessage().contains("Failed to create transaction savepoint"));
    }

    @Test
    void shouldHandleErrorInOutermostTransaction() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);

        Error error = assertThrows(OutOfMemoryError.class, () ->
                transactionManager.runInTransaction(() -> {
                    throw new OutOfMemoryError("OOM");
                })
        );

        assertEquals("OOM", error.getMessage());
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void shouldHandleErrorInNestedTransaction() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(false);
        Savepoint savepoint = mock(Savepoint.class);
        when(connection.setSavepoint()).thenReturn(savepoint);

        Error error = assertThrows(OutOfMemoryError.class, () ->
                transactionManager.runInTransaction(() -> {
                    throw new OutOfMemoryError("OOM");
                })
        );

        assertEquals("OOM", error.getMessage());
        verify(connection).rollback(savepoint);
    }

    @Test
    void shouldReturnNullFromSuccessfulTransaction() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);

        Object result = transactionManager.runInTransaction(() -> null);

        assertNull(result);
        verify(connection).commit();
    }
}
