package com.possum.infrastructure.db;

import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.persistence.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private AppPaths appPaths;
    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() {
        appPaths = mock(AppPaths.class);
        when(appPaths.getDatabasePath()).thenReturn(tempDir.resolve("test.db"));
        databaseManager = new DatabaseManager(appPaths);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            try {
                databaseManager.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void initialize_successful_createsConnection() {
        databaseManager.initialize();

        Connection connection = databaseManager.getConnection();

        assertNotNull(connection);
        assertDoesNotThrow(() -> assertFalse(connection.isClosed()));
    }

    @Test
    void initialize_migrationExecution_appliesMigrations() {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();

        assertDoesNotThrow(() -> {
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");
                assertTrue(rs.next());
            }
        });
    }

    @Test
    void initialize_connectionValidation_enablesForeignKeys() throws SQLException {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void getConnection_validConnection_returnsConnection() {
        databaseManager.initialize();

        Connection connection = databaseManager.getConnection();

        assertNotNull(connection);
    }

    @Test
    void getConnection_multipleCallsSameConnection_returnsSameInstance() {
        databaseManager.initialize();

        Connection conn1 = databaseManager.getConnection();
        Connection conn2 = databaseManager.getConnection();

        assertSame(conn1, conn2);
    }

    @Test
    void closeConnection_cleanup_closesConnection() throws SQLException {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();

        databaseManager.close();

        assertTrue(connection.isClosed());
    }

    @Test
    void getConnection_afterClose_reinitializes() throws SQLException {
        databaseManager.initialize();
        Connection firstConnection = databaseManager.getConnection();
        databaseManager.close();

        Connection secondConnection = databaseManager.getConnection();

        assertNotNull(secondConnection);
        assertFalse(secondConnection.isClosed());
        assertNotSame(firstConnection, secondConnection);
    }

    @Test
    void initialize_walMode_enablesWAL() throws SQLException {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA journal_mode");
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase());
        }
    }

    @Test
    void initialize_busyTimeout_setsTimeout() throws SQLException {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout");
            assertTrue(rs.next());
            assertEquals(5000, rs.getInt(1));
        }
    }

    @Test
    void getConnection_staleConnection_reinitializes() throws SQLException {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();
        connection.close();

        Connection newConnection = databaseManager.getConnection();

        assertNotNull(newConnection);
        assertFalse(newConnection.isClosed());
    }

    @Test
    void initialize_alreadyInitialized_doesNotReinitialize() {
        databaseManager.initialize();
        Connection firstConnection = databaseManager.getConnection();

        databaseManager.initialize();
        Connection secondConnection = databaseManager.getConnection();

        assertSame(firstConnection, secondConnection);
    }

    @Test
    void close_notInitialized_doesNotThrow() {
        assertDoesNotThrow(() -> databaseManager.close());
    }

    @Test
    void getConnection_autoCommitEnabled_returnsAutoCommitConnection() throws SQLException {
        databaseManager.initialize();

        Connection connection = databaseManager.getConnection();

        assertTrue(connection.getAutoCommit());
    }

    @Test
    void initialize_schemaVersion_executesHealthCheck() throws SQLException {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA schema_version");
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 0);
        }
    }

    @Test
    void close_multipleClose_doesNotThrow() {
        databaseManager.initialize();

        databaseManager.close();
        assertDoesNotThrow(() -> databaseManager.close());
    }
}
