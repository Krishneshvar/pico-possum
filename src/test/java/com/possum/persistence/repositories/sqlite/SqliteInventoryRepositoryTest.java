package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.InventoryAdjustment;
import com.possum.domain.model.InventoryLot;
import com.possum.domain.model.Product;
import com.possum.shared.dto.AvailableLot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteInventoryRepositoryTest {

    private Connection connection;
    private SqliteInventoryRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteInventoryRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                category_id INTEGER,
                category_name TEXT,
                sku TEXT,
                mrp REAL,
                cost_price REAL,
                stock_alert_cap INTEGER DEFAULT 10,
                status TEXT DEFAULT 'active',
                image_path TEXT,
                stock INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_lots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                batch_number TEXT,
                manufactured_date TEXT,
                expiry_date TEXT,
                quantity INTEGER NOT NULL,
                unit_cost REAL,
                purchase_order_item_id INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_adjustments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                lot_id INTEGER,
                quantity_change INTEGER NOT NULL,
                reason TEXT NOT NULL,
                reference_type TEXT,
                reference_id INTEGER,
                adjusted_by INTEGER,
                notes TEXT,
                adjusted_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    @Test
    void insertLot_validLot_insertsSuccessfully() {
        InventoryLot lot = new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null);
        long id = repository.insertInventoryLot(lot);
        assertTrue(id > 0);
    }

    @Test
    void findLotById_found_returnsLot() {
        InventoryLot lot = new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null);
        long id = repository.insertInventoryLot(lot);
        Optional<InventoryLot> result = repository.findLotById(id);
        assertTrue(result.isPresent());
        assertEquals(100, result.get().quantity());
    }

    @Test
    void getStockByProductId_aggregation_returnsTotal() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name, sku, mrp, cost_price) VALUES (1, 'Product', 'SKU001', 100, 50)");
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH002", null, null, 50, new BigDecimal("50.00"), null, null));

        int stock = repository.getStockByProductId(1L);
        assertEquals(150, stock);
    }

    @Test
    void findAvailableLots_FIFO_ordering() {
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH003", null, null, 30, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null));

        List<AvailableLot> result = repository.findAvailableLotsByProductId(1L);
        assertEquals(2, result.size());
    }

    @Test
    void findLowStockProducts_threshold_returnsProducts() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name, sku, mrp, cost_price, stock_alert_cap) VALUES (1, 'Low Stock', 'SKU001', 100, 50, 100)");
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 50, new BigDecimal("50.00"), null, null));

        List<Product> result = repository.findLowStockProducts();
        assertEquals(1, result.size());
        assertEquals("Low Stock", result.get(0).name());
    }

    @Test
    void insertAdjustment_validAdjustment_insertsSuccessfully() {
        InventoryAdjustment adjustment = new InventoryAdjustment(null, 1L, null, 10, "correction", null, null, 1L, null, null);
        long id = repository.insertInventoryAdjustment(adjustment);
        assertTrue(id > 0);
    }
}
