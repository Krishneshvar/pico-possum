package com.possum.persistence.db;

import com.possum.infrastructure.filesystem.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Mock
    private AppPaths appPaths;

    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        Path dbFile = tempDir.resolve("possum_test.db");
        when(appPaths.getDatabasePath()).thenReturn(dbFile);
        
        databaseManager = new DatabaseManager(appPaths);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void initialize_createsDatabaseAndRunsMigrations() throws SQLException {
        databaseManager.initialize();
        
        Connection connection = databaseManager.getConnection();
        assertNotNull(connection);
        assertFalse(connection.isClosed());
        
        // Verify PRAGMAs
        try (Statement stmt = connection.createStatement()) {
            assertTrue(stmt.executeQuery("PRAGMA foreign_keys").getInt(1) == 1);
            assertEquals("wal", stmt.executeQuery("PRAGMA journal_mode").getString(1).toLowerCase());
        }
    }

    @Test
    void getConnection_initializesOnFirstCall() throws SQLException {
        Connection connection = databaseManager.getConnection();
        assertNotNull(connection);
        assertFalse(connection.isClosed());
    }

    @Test
    void close_closesConnection() throws SQLException {
        databaseManager.initialize();
        Connection connection = databaseManager.getConnection();
        assertFalse(connection.isClosed());
        
        databaseManager.close();
        assertTrue(connection.isClosed());
    }

    @Test
    void initialize_multipleCalls_isIdempotent() throws SQLException {
        databaseManager.initialize();
        Connection first = databaseManager.getConnection();
        
        databaseManager.initialize();
        Connection second = databaseManager.getConnection();
        
        assertSame(first, second);
    }

    @Test
    void isConnectionStale_handlesCorruptionOrClose() throws SQLException {
        Connection connection = databaseManager.getConnection();
        assertNotNull(connection);
        
        // Simulating external close or corruption if possible
        // Actually DatabaseManager.getConnection() checks if isConnectionStale()
        // We can check if it recreates connection if we close it manually
        connection.close();
        
        Connection newConnection = databaseManager.getConnection();
        assertNotSame(connection, newConnection);
        assertFalse(newConnection.isClosed());
    }

    @Test
    void constructor_nullAppPaths_throwsException() {
        assertThrows(NullPointerException.class, () -> new DatabaseManager(null));
    }
}
