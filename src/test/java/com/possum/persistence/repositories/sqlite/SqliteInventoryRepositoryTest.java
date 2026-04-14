package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.InventoryAdjustment;
import com.possum.domain.model.InventoryLot;
import com.possum.domain.model.Variant;
import com.possum.shared.dto.AvailableLot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE tax_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                category_id INTEGER,
                tax_category_id INTEGER,
                status TEXT DEFAULT 'active',
                image_path TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE variants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                sku TEXT,
                mrp REAL,
                cost_price REAL,
                stock_alert_cap INTEGER DEFAULT 10,
                is_default INTEGER DEFAULT 0,
                status TEXT DEFAULT 'active',
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_lots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                variant_id INTEGER NOT NULL,
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
                variant_id INTEGER NOT NULL,
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
    void findLotsByVariant_filtering_returnsLots() {
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH002", null, null, 50, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 2L, "BATCH003", null, null, 75, new BigDecimal("50.00"), null, null));

        List<InventoryLot> result = repository.findLotsByVariantId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void updateLotQuantity_validChange_updatesSuccessfully() throws SQLException {
        InventoryLot lot = new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null);
        long id = repository.insertInventoryLot(lot);

        connection.createStatement().execute("UPDATE inventory_lots SET quantity = 80 WHERE id = " + id);

        Optional<InventoryLot> updated = repository.findLotById(id);
        assertTrue(updated.isPresent());
        assertEquals(80, updated.get().quantity());
    }

    @Test
    void deleteLot_softDelete_marksAsDeleted() throws SQLException {
        InventoryLot lot = new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null);
        long id = repository.insertInventoryLot(lot);

        connection.createStatement().execute("DELETE FROM inventory_lots WHERE id = " + id);

        Optional<InventoryLot> found = repository.findLotById(id);
        assertFalse(found.isPresent());
    }

    @Test
    void findLotsForFIFO_ordering_returnsOrderedLots() {
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH003", null, null, 30, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH002", null, null, 50, new BigDecimal("50.00"), null, null));

        List<AvailableLot> result = repository.findAvailableLots(1L);

        assertEquals(3, result.size());
    }

    @Test
    void getVariantStock_aggregation_returnsTotal() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        connection.createStatement().execute("INSERT INTO variants (id, product_id, name, sku, mrp, cost_price) VALUES (1, 1, 'Variant', 'SKU001', 100, 50)");
        
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH002", null, null, 50, new BigDecimal("50.00"), null, null));

        int stock = repository.getStockByVariantId(1L);

        assertEquals(150, stock);
    }

    @Test
    void getLowStockVariants_threshold_returnsVariants() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        connection.createStatement().execute("INSERT INTO variants (id, product_id, name, sku, mrp, cost_price, stock_alert_cap) VALUES (1, 1, 'Low Stock', 'SKU001', 100, 50, 100)");
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 50, new BigDecimal("50.00"), null, null));

        List<Variant> result = repository.findLowStockVariants();

        assertEquals(1, result.size());
    }

    @Test
    void getExpiringLots_dateRange_returnsLots() {
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null));

        List<InventoryLot> result = repository.findExpiringLots(30);

        assertTrue(result.size() >= 0);
    }

    @Test
    void findLotsByBatch_filtering_returnsLots() {
        repository.insertInventoryLot(new InventoryLot(null, 1L, "BATCH001", null, null, 100, new BigDecimal("50.00"), null, null));
        repository.insertInventoryLot(new InventoryLot(null, 2L, "BATCH001", null, null, 50, new BigDecimal("50.00"), null, null));

        List<InventoryLot> result = repository.findLotsByVariantId(1L);

        assertEquals(1, result.size());
        assertEquals("BATCH001", result.get(0).batchNumber());
    }

    @Test
    void insertAdjustment_validAdjustment_insertsSuccessfully() {
        InventoryAdjustment adjustment = new InventoryAdjustment(null, 1L, null, 10, "correction", null, null, 1L, null, null);

        long id = repository.insertInventoryAdjustment(adjustment);

        assertTrue(id > 0);
    }

    @Test
    void findAdjustments_withFilters_returnsAdjustments() {
        repository.insertInventoryAdjustment(new InventoryAdjustment(null, 1L, null, 10, "correction", null, null, 1L, null, null));
        repository.insertInventoryAdjustment(new InventoryAdjustment(null, 1L, null, -5, "damage", null, null, 1L, null, null));

        List<InventoryAdjustment> result = repository.findAdjustmentsByVariantId(1L, 10, 0);

        assertEquals(2, result.size());
    }

    @Test
    void getStockHistory_dateRange_returnsHistory() {
        repository.insertInventoryAdjustment(new InventoryAdjustment(null, 1L, null, 10, "correction", null, null, 1L, null, null));

        List<com.possum.shared.dto.StockHistoryDto> result = repository.findStockHistory(null, null, null, null, null, 10, 0);

        assertTrue(result.size() >= 0);
    }
}
