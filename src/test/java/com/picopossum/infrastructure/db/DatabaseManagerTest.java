package com.picopossum.infrastructure.db;

import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.persistence.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseManagerTest {

    private Path tempDir;
    private AppPaths appPaths;
    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("possum-db-test-");
        appPaths = mock(AppPaths.class);
        when(appPaths.getDatabasePath()).thenReturn(tempDir.resolve("test.db"));
        databaseManager = new DatabaseManager(appPaths);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (databaseManager != null) {
            try {
                databaseManager.close();
            } catch (Exception ignored) {
            }
        }
        // Use resilient cleanup for Windows
        deleteDirectory(tempDir);
    }

    @Test
    void initialize_successful_createsConnection() throws SQLException {
        databaseManager.initialize();

        try (Connection connection = databaseManager.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void initialize_migrationExecution_appliesMigrations() {
        databaseManager.initialize();

        assertDoesNotThrow(() -> {
            try (Connection connection = databaseManager.getConnection();
                 Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");
                assertTrue(rs.next());
            }
        });
    }

    @Test
    void initialize_connectionValidation_enablesForeignKeys() throws SQLException {
        databaseManager.initialize();

        try (Connection connection = databaseManager.getConnection();
             Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void getConnection_validConnection_returnsConnection() {
        databaseManager.initialize();

        try (Connection connection = databaseManager.getConnection()) {
            assertNotNull(connection);
        } catch (SQLException e) {
            fail(e);
        }
    }

    @Test
    void getConnection_multipleCallsSameConnection_returnsValidConnections() throws SQLException {
        databaseManager.initialize();

        try (Connection conn1 = databaseManager.getConnection();
             Connection conn2 = databaseManager.getConnection()) {
            assertNotNull(conn1);
            assertNotNull(conn2);
            assertFalse(conn1.isClosed());
            assertFalse(conn2.isClosed());
        }
    }

    @Test
    void closeConnection_cleanup_closesPool() throws SQLException {
        databaseManager.initialize();
        try (Connection connection = databaseManager.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }

        databaseManager.close();
        // Pool is null, next getConnection re-initializes
    }

    @Test
    void getConnection_afterClose_reinitializes() throws SQLException {
        databaseManager.initialize();
        Connection firstConnection = databaseManager.getConnection();
        firstConnection.close(); // Close proxy
        databaseManager.close();

        try (Connection secondConnection = databaseManager.getConnection()) {
            assertNotNull(secondConnection);
            assertFalse(secondConnection.isClosed());
            assertNotSame(firstConnection, secondConnection);
        }
    }

    @Test
    void initialize_walMode_enablesWAL() throws SQLException {
        databaseManager.initialize();
        try (Connection connection = databaseManager.getConnection();
             Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA journal_mode");
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase());
        }
    }

    @Test
    void initialize_busyTimeout_setsTimeout() throws SQLException {
        databaseManager.initialize();
        try (Connection connection = databaseManager.getConnection();
             Statement stmt = connection.createStatement()) {
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

        try (Connection newConnection = databaseManager.getConnection()) {
            assertNotNull(newConnection);
            assertFalse(newConnection.isClosed());
            assertNotSame(connection, newConnection);
        }
    }

    @Test
    void initialize_alreadyInitialized_doesNotReinitialize() throws SQLException {
        databaseManager.initialize();
        try (Connection firstConnection = databaseManager.getConnection()) {
            databaseManager.initialize();
            try (Connection secondConnection = databaseManager.getConnection()) {
                assertNotNull(firstConnection);
                assertNotNull(secondConnection);
                assertFalse(firstConnection.isClosed());
                assertFalse(secondConnection.isClosed());
            }
        }
    }

    @Test
    void close_notInitialized_doesNotThrow() {
        assertDoesNotThrow(() -> databaseManager.close());
    }

    @Test
    void getConnection_autoCommitEnabled_returnsAutoCommitConnection() throws SQLException {
        databaseManager.initialize();

        try (Connection connection = databaseManager.getConnection()) {
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void initialize_schemaVersion_executesHealthCheck() throws SQLException {
        databaseManager.initialize();

        try (Connection connection = databaseManager.getConnection();
             Statement stmt = connection.createStatement()) {
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

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        
        for (int i = 0; i < 5; i++) {
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        // Best effort
                    }
                });
            }
            if (!Files.exists(root)) return;
            System.gc();
            System.runFinalization();
            try { Thread.sleep(200 * (i + 1)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
