package com.possum.persistence.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class HikariConnectionPool implements ConnectionProvider, AutoCloseable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HikariConnectionPool.class);
    
    private final HikariDataSource dataSource;
    
    public HikariConnectionPool(String jdbcUrl) {
        this(jdbcUrl, createDefaultConfig());
    }
    
    public HikariConnectionPool(String jdbcUrl, PoolConfig poolConfig) {
        HikariConfig config = new HikariConfig();
        
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        
        // Pool sizing
        config.setMinimumIdle(poolConfig.minimumIdle);
        config.setMaximumPoolSize(poolConfig.maximumPoolSize);
        
        // Connection timeouts
        config.setConnectionTimeout(poolConfig.connectionTimeoutMs);
        config.setIdleTimeout(poolConfig.idleTimeoutMs);
        config.setMaxLifetime(poolConfig.maxLifetimeMs);
        
        // Validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(poolConfig.validationTimeoutMs);
        
        // Leak detection
        config.setLeakDetectionThreshold(poolConfig.leakDetectionThresholdMs);
        
        // Pool name for monitoring
        config.setPoolName("POSSUM-SQLite-Pool");
        
        // SQLite specific properties
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        
        // Performance tuning
        config.setAutoCommit(true);
        config.setReadOnly(false);
        
        // Metrics
        config.setRegisterMbeans(true);
        
        this.dataSource = new HikariDataSource(config);
        
        LOGGER.info("HikariCP connection pool initialized: minIdle={}, maxPool={}", 
            poolConfig.minimumIdle, poolConfig.maximumPoolSize);
        
        // Verify pool is working
        verifyPool();
    }
    
    private void verifyPool() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA schema_version");
            LOGGER.info("Connection pool verification successful");
        } catch (SQLException e) {
            LOGGER.error("Connection pool verification failed", e);
            throw new IllegalStateException("Failed to verify connection pool", e);
        }
    }
    
    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            LOGGER.error("Failed to get connection from pool", e);
            throw new IllegalStateException("Failed to get database connection", e);
        }
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOGGER.info("Closing HikariCP connection pool");
            dataSource.close();
        }
    }
    
    public PoolStats getPoolStats() {
        return new PoolStats(
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
    
    private static PoolConfig createDefaultConfig() {
        return new PoolConfig(
            2,      // minimumIdle
            10,     // maximumPoolSize
            30000,  // connectionTimeoutMs (30s)
            600000, // idleTimeoutMs (10min)
            1800000,// maxLifetimeMs (30min)
            5000,   // validationTimeoutMs (5s)
            60000   // leakDetectionThresholdMs (1min)
        );
    }
    
    public static class PoolConfig {
        final int minimumIdle;
        final int maximumPoolSize;
        final long connectionTimeoutMs;
        final long idleTimeoutMs;
        final long maxLifetimeMs;
        final long validationTimeoutMs;
        final long leakDetectionThresholdMs;
        
        public PoolConfig(int minimumIdle, int maximumPoolSize, 
                         long connectionTimeoutMs, long idleTimeoutMs, 
                         long maxLifetimeMs, long validationTimeoutMs,
                         long leakDetectionThresholdMs) {
            this.minimumIdle = minimumIdle;
            this.maximumPoolSize = maximumPoolSize;
            this.connectionTimeoutMs = connectionTimeoutMs;
            this.idleTimeoutMs = idleTimeoutMs;
            this.maxLifetimeMs = maxLifetimeMs;
            this.validationTimeoutMs = validationTimeoutMs;
            this.leakDetectionThresholdMs = leakDetectionThresholdMs;
        }
    }
    
    public record PoolStats(
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int threadsAwaiting
    ) {}
}
