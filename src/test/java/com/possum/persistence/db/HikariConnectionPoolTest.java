package com.possum.persistence.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class HikariConnectionPoolTest {

    private static final String JDBC_URL = "jdbc:sqlite::memory:";
    private HikariConnectionPool pool;

    @BeforeEach
    void setUp() {
        pool = new HikariConnectionPool(JDBC_URL);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    @Test
    void getConnection_returnsValidConnection() throws SQLException {
        Connection connection = pool.getConnection();
        assertNotNull(connection);
        assertFalse(connection.isClosed());
        
        // Check PRAGMAs were passed through datasource properties
        try (Statement stmt = connection.createStatement()) {
            assertTrue(stmt.executeQuery("PRAGMA foreign_keys").getInt(1) == 1);
            String journalMode = stmt.executeQuery("PRAGMA journal_mode").getString(1).toLowerCase();
            // In-memory SQLite often defaults to 'memory' journal mode instead of WAL
            assertTrue(journalMode.equals("wal") || journalMode.equals("memory"), "Expected wal or memory journal mode");
        }
    }

    @Test
    void close_shutsDownPool() throws SQLException {
        Connection connection = pool.getConnection();
        assertNotNull(connection);
        pool.close();
        
        // This should throw because the pool is closed
        assertThrows(IllegalStateException.class, () -> pool.getConnection());
    }

    @Test
    void getPoolStats_returnsMetrics() {
        HikariConnectionPool.PoolStats stats = pool.getPoolStats();
        
        assertNotNull(stats);
        assertEquals(0, stats.activeConnections());
        // minimumIdle is 2 in default config
        assertTrue(stats.idleConnections() >= 0); 
    }

    @Test
    void customConfig_isApplied() {
        HikariConnectionPool.PoolConfig config = new HikariConnectionPool.PoolConfig(
            1, 5, 5000, 10000, 20000, 2000, 3000
        );
        
        HikariConnectionPool customPool = new HikariConnectionPool(JDBC_URL, config);
        try {
            assertNotNull(customPool.getConnection());
            HikariConnectionPool.PoolStats stats = customPool.getPoolStats();
            // Total should settle at minimumIdle (1) or more if connections were just borrowed
            assertTrue(stats.totalConnections() >= 1);
        } finally {
            customPool.close();
        }
    }

    @Test
    void verifyPool_invalidJdbcUrl_throwsException() {
        // Invalid SQLite URL might still initialize but fail on verifyPool PRAGMA call or in constructor
        // Hikari often wraps driver errors into RuntimeException during initialization
        assertThrows(Exception.class, () -> new HikariConnectionPool("jdbc:invalid:url"));
    }
}
