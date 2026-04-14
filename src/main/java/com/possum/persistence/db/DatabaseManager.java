package com.possum.persistence.db;

import com.possum.infrastructure.filesystem.AppPaths;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class DatabaseManager implements ConnectionProvider, AutoCloseable {

    private final AppPaths appPaths;
    private Connection connection;

    public DatabaseManager(AppPaths appPaths) {
        this.appPaths = Objects.requireNonNull(appPaths, "appPaths must not be null");
    }

    public synchronized void initialize() {
        if (isConnectionOpen()) {
            return;
        }

        Path databasePath = appPaths.getDatabasePath().toAbsolutePath();
        String jdbcUrl = "jdbc:sqlite:" + databasePath;

        runMigrations(jdbcUrl);

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5_000);

        Connection candidate = null;

        try {
            candidate = DriverManager.getConnection(jdbcUrl, config.toProperties());
            candidate.setAutoCommit(true);
            try (Statement statement = candidate.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA schema_version"); // startup health check
            }
            connection = candidate;
        } catch (SQLException ex) {
            closeQuietly(candidate);
            throw new IllegalStateException("Failed to initialize database connection", ex);
        }
    }

    private static void runMigrations(String jdbcUrl) {
        ensureCodeColumnExists(jdbcUrl);
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations("classpath:sql/migrations")
                .validateMigrationNaming(true)
                .mixed(true)
                .load();
        flyway.repair();
        flyway.migrate();
    }

    private static void ensureCodeColumnExists(String jdbcUrl) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            
            // 1. Check if payment_methods table exists
            boolean tableExists = false;
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='payment_methods'")) {
                if (rs.next()) {
                    tableExists = true;
                }
            }
            
            if (tableExists) {
                // 2. Check if code column exists
                boolean columnExists = false;
                try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(payment_methods)")) {
                    while (rs.next()) {
                        if ("code".equalsIgnoreCase(rs.getString("name"))) {
                            columnExists = true;
                            break;
                        }
                    }
                }
                
                // 3. Add column if missing
                if (!columnExists) {
                    stmt.execute("ALTER TABLE payment_methods ADD COLUMN code TEXT");
                }
            }
        } catch (SQLException ex) {
            // Log it but don't fail, let Flyway try its best later
            System.err.println("Database self-healing: Failed to ensure 'code' column: " + ex.getMessage());
        }
    }

    @Override
    public synchronized Connection getConnection() {
        if (!isConnectionOpen() || isConnectionStale()) {
            initialize();
        }
        return connection;
    }

    @Override
    public synchronized void close() {
        if (!isConnectionOpen()) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to close database connection", ex);
        } finally {
            connection = null;
        }
    }

    private boolean isConnectionStale() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA schema_version");
            return false;
        } catch (SQLException ex) {
            return true;
        }
    }

    private boolean isConnectionOpen() {
        if (connection == null) {
            return false;
        }

        try {
            return !connection.isClosed();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to evaluate database connection state", ex);
        }
    }

    private static void closeQuietly(Connection candidate) {
        if (candidate == null) {
            return;
        }
        try {
            candidate.close();
        } catch (SQLException ignored) {
            // Best effort cleanup during failed initialization.
        }
    }
}
