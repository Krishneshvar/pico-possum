package com.picopossum.persistence.db;

import com.picopossum.infrastructure.filesystem.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DatabaseManagerTest {

    private Path tempDir;

    @Mock
    private AppPaths appPaths;

    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        tempDir = Files.createTempDirectory("possum-persistence-db-test-");
        Path dbFile = tempDir.resolve("possum_test.db");
        when(appPaths.getDatabasePath()).thenReturn(dbFile);
        
        databaseManager = new DatabaseManager(appPaths);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (databaseManager != null) {
            databaseManager.close();
        }
        // Use resilient cleanup for Windows
        deleteDirectory(tempDir);
    }

    @Test
    void initialize_createsDatabaseAndRunsMigrations() throws SQLException {
        databaseManager.initialize();
        
        try (Connection connection = databaseManager.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
            
            // Verify PRAGMAs
            try (Statement stmt = connection.createStatement()) {
                assertTrue(stmt.executeQuery("PRAGMA foreign_keys").getInt(1) == 1);
                assertEquals("wal", stmt.executeQuery("PRAGMA journal_mode").getString(1).toLowerCase());
            }
        }
    }

    @Test
    void getConnection_initializesOnFirstCall() throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void close_shutsDownPool() throws SQLException {
        databaseManager.initialize();
        try (Connection connection = databaseManager.getConnection()) {
            assertFalse(connection.isClosed());
        }
        
        databaseManager.close();
        // The pool is null now, so the next getConnection will re-initialize
    }

    @Test
    void initialize_multipleCalls_isIdempotent() throws SQLException {
        databaseManager.initialize();
        try (Connection first = databaseManager.getConnection()) {
            databaseManager.initialize();
            try (Connection second = databaseManager.getConnection()) {
                assertNotNull(first);
                assertNotNull(second);
                assertFalse(first.isClosed());
                assertFalse(second.isClosed());
            }
        }
    }

    @Test
    void isConnectionStale_handlesCorruptionOrClose() throws SQLException {
        Connection connection = databaseManager.getConnection();
        assertNotNull(connection);
        
        // Manual close to simulate "stale"
        connection.close();
        
        Connection newConnection = databaseManager.getConnection();
        assertNotNull(newConnection);
        assertFalse(newConnection.isClosed());
        // Should be a different connection since the first was closed
        assertNotSame(connection, newConnection);
        newConnection.close();
    }

    @Test
    void constructor_nullAppPaths_throwsException() {
        assertThrows(NullPointerException.class, () -> new DatabaseManager(null));
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
