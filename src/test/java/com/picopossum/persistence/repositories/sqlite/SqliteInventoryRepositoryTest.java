package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.StockMovement;
import com.picopossum.domain.model.Product;
import com.picopossum.shared.dto.StockHistoryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqliteInventoryRepositoryTest {

    private Connection connection;
    private SqliteInventoryRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteInventoryRepository(new com.picopossum.persistence.db.ConnectionProvider() {
            @Override public Connection getConnection() { return connection; }
            @Override public boolean isBound(Connection conn) { return true; }
        });
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                parent_id INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                category_id INTEGER,
                tax_rate REAL,
                sku TEXT,
                barcode TEXT,
                mrp REAL,
                cost_price REAL,
                stock_alert_cap INTEGER DEFAULT 10,
                status TEXT DEFAULT 'active',
                image_path TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE product_stock_cache (
                product_id INTEGER PRIMARY KEY,
                current_stock INTEGER DEFAULT 0,
                last_updated TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                quantity_change INTEGER NOT NULL,
                reason TEXT NOT NULL,
                reference_type TEXT,
                reference_id INTEGER,
                notes TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
        """);
        
        // Add a trigger-like manual sync for testing purposes or mock trigger behavior
        // In the real app, we use DB triggers. In the test, we'll manually insert into cache
    }

    private void syncCache(long productId, int stock) throws SQLException {
        connection.createStatement().execute("INSERT OR REPLACE INTO product_stock_cache (product_id, current_stock) VALUES (" + productId + ", " + stock + ")");
    }

    @Test
    void getStockByProductId_returnsCacheValue() throws SQLException {
        syncCache(1L, 50);
        int stock = repository.getStockByProductId(1L);
        assertEquals(50, stock);
    }

    @Test
    void insertStockMovement_insertsSuccessfully() {
        StockMovement movement = new StockMovement(null, 1L, 10, "receive", "manual", null, "Restock", LocalDateTime.now());
        long id = repository.insertStockMovement(movement);
        assertTrue(id > 0);
    }

    @Test
    void findMovementsByProductId_returnsList() {
        repository.insertStockMovement(new StockMovement(null, 1L, 10, "receive", "manual", null, "R1", LocalDateTime.now()));
        repository.insertStockMovement(new StockMovement(null, 1L, -5, "sale", "sale", 100L, "S1", LocalDateTime.now()));

        List<StockMovement> movements = repository.findMovementsByProductId(1L, 10, 0);
        assertEquals(2, movements.size());
    }

    @Test
    void findLowStockProducts_returnsFilteredList() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name, sku, stock_alert_cap) VALUES (1, 'Low', 'SKU1', 10)");
        connection.createStatement().execute("INSERT INTO products (id, name, sku, stock_alert_cap) VALUES (2, 'High', 'SKU2', 10)");
        
        syncCache(1L, 5); // Below alert
        syncCache(2L, 15); // Above alert

        List<Product> lowStock = repository.findLowStockProducts();
        assertEquals(1, lowStock.size());
        assertEquals("Low", lowStock.get(0).name());
    }

    @Test
    void getInventoryStats_returnsCorrectSummary() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name, stock_alert_cap) VALUES (1, 'P1', 10)");
        connection.createStatement().execute("INSERT INTO products (id, name, stock_alert_cap) VALUES (2, 'P2', 10)");
        
        syncCache(1L, 5); // Low
        syncCache(2L, 0); // No stock + Low
        
        Map<String, Object> stats = repository.getInventoryStats();
        assertEquals(5, stats.get("totalItemsInStock"));
        assertEquals(1, stats.get("productsWithNoStock"));
        assertEquals(2, stats.get("productsWithLowStock"));
    }
}
