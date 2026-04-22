package com.picopossum.persistence.db;

import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.persistence.repositories.sqlite.*;
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
    private HikariConnectionPool pool;

    public DatabaseManager(AppPaths appPaths) {
        this.appPaths = Objects.requireNonNull(appPaths, "appPaths must not be null");
    }

    public synchronized void initialize() {
        if (pool != null) {
            return;
        }

        Path databasePath = appPaths.getDatabasePath().toAbsolutePath();
        String jdbcUrl = "jdbc:sqlite:" + databasePath;

        // Perform migrations and self-healing using direct connections before initializing the pool
        runMigrations(jdbcUrl);
        ensurePosOpenBillItemsCorrect(jdbcUrl);
        ensureCodeColumnExists(jdbcUrl);

        // Initialize connection pool
        pool = new HikariConnectionPool(jdbcUrl);
        
        com.picopossum.infrastructure.logging.LoggingConfig.getLogger().info("Database system initialized with HikariCP pool");
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

    private static void ensurePosOpenBillItemsCorrect(String jdbcUrl) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            
            boolean tableExists = false;
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='pos_open_bill_items'")) {
                if (rs.next()) tableExists = true;
            }
            
            if (tableExists) {
                boolean hasProductId = false;
                boolean hasVariantId = false;
                try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(pos_open_bill_items)")) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        if ("product_id".equalsIgnoreCase(name)) hasProductId = true;
                        if ("variant_id".equalsIgnoreCase(name)) hasVariantId = true;
                    }
                }
                
                if (!hasProductId && hasVariantId) {
                    // Rename variant_id to product_id
                    stmt.execute("ALTER TABLE pos_open_bill_items RENAME COLUMN variant_id TO product_id");
                    System.out.println("Database self-healing: Renamed 'variant_id' to 'product_id' in pos_open_bill_items");
                }
            }
        } catch (SQLException ex) {
            System.err.println("Database self-healing: Failed to fix pos_open_bill_items: " + ex.getMessage());
        }
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

    private final ThreadLocal<Connection> activeConnection = new ThreadLocal<>();

    @Override
    public Connection getConnection() {
        Connection conn = activeConnection.get();
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    return conn;
                }
            } catch (SQLException ignored) {
            }
            activeConnection.remove();
        }

        if (pool == null) {
            initialize();
        }
        return pool.getConnection();
    }

    @Override
    public boolean isBound(Connection connection) {
        return connection == activeConnection.get();
    }

    /**
     * Binds a connection to the current thread. 
     * Used by TransactionManager to ensure all repository calls within a transaction block use the same connection.
     */
    public void bindConnection(Connection connection) {
        activeConnection.set(connection);
    }

    /**
     * Unbinds the connection from the current thread.
     */
    public void unbindConnection() {
        activeConnection.remove();
    }

    @Override
    public synchronized void close() {
        activeConnection.remove();
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    public SqliteProductRepository getProductRepository() {
        return new SqliteProductRepository(this);
    }

    public SqliteInventoryRepository getInventoryRepository() {
        return new SqliteInventoryRepository(this);
    }
}
