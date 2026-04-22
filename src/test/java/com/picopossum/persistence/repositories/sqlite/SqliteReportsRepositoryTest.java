package com.picopossum.persistence.repositories.sqlite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqliteReportsRepositoryTest {

    private Connection connection;
    private SqliteReportsRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        seedData();
        repository = new SqliteReportsRepository(new com.picopossum.persistence.db.ConnectionProvider() {
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
        connection.createStatement().execute("CREATE TABLE categories (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, category_id INTEGER, sku TEXT, mrp REAL, cost_price REAL, stock_alert_cap INTEGER DEFAULT 10, status TEXT DEFAULT 'active', deleted_at TEXT)");
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT, code TEXT, is_active INTEGER DEFAULT 1)");
        connection.createStatement().execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, email TEXT, customer_type TEXT)");
        connection.createStatement().execute("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, user_id INTEGER, invoice_number TEXT, invoice_id TEXT, status TEXT DEFAULT 'completed', fulfillment_status TEXT DEFAULT 'fulfilled', total_amount REAL DEFAULT 0, paid_amount REAL DEFAULT 0, discount REAL DEFAULT 0, payment_method_id INTEGER, sale_date TEXT DEFAULT CURRENT_TIMESTAMP)");
        connection.createStatement().execute("CREATE TABLE returns (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT, sale_id INTEGER, user_id INTEGER, reason TEXT, refund_amount REAL, payment_method_id INTEGER, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        connection.createStatement().execute("CREATE TABLE sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER, product_id INTEGER, quantity INTEGER, price_per_unit REAL, cost_per_unit REAL DEFAULT 0, discount_amount REAL DEFAULT 0)");
        connection.createStatement().execute("CREATE TABLE legacy_sales (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_number TEXT, sale_date TEXT, net_amount REAL)");
        connection.createStatement().execute("CREATE TABLE inventory_lots (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, quantity INTEGER, unit_cost REAL, created_at TEXT)");
        connection.createStatement().execute("CREATE TABLE product_flow (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, event_type TEXT, quantity INTEGER, event_date TEXT DEFAULT CURRENT_TIMESTAMP)");
        connection.createStatement().execute("CREATE TABLE inventory_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, quantity_change INTEGER, reason TEXT)");
    }

    private void seedData() throws SQLException {
        connection.createStatement().execute("INSERT INTO payment_methods (id, name, code) VALUES (1, 'Cash', 'CH')");
        connection.createStatement().execute("INSERT INTO payment_methods (id, name, code) VALUES (2, 'UPI', 'UP')");
        connection.createStatement().execute("INSERT INTO customers (id, name) VALUES (1, 'John Doe')");
        connection.createStatement().execute("INSERT INTO categories VALUES (1, 'Electronics')");
        
        // Products with MRP/Cost
        connection.createStatement().execute("INSERT INTO products (id, name, category_id, sku, mrp, cost_price, stock_alert_cap) VALUES (1, 'Laptop', 1, 'LAP1', 1000.0, 600.0, 5)");
        connection.createStatement().execute("INSERT INTO products (id, name, category_id, sku, mrp, cost_price, stock_alert_cap) VALUES (2, 'Phone', 1, 'PHO1', 500.0, 300.0, 5)");

        // Sale 1
        connection.createStatement().execute("INSERT INTO sales (id, customer_id, user_id, invoice_number, invoice_id, status, total_amount, paid_amount, discount, payment_method_id, sale_date) VALUES (1, 1, 1, 'INV-001', 'INV-001', 'completed', 1000.0, 1000.0, 10.0, 1, '2025-06-01 10:00:00')");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, product_id, quantity, price_per_unit, cost_per_unit, discount_amount) VALUES (1, 1, 1, 3, 300.0, 200.0, 5.0)");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, product_id, quantity, price_per_unit, cost_per_unit, discount_amount) VALUES (2, 1, 2, 1, 100.0, 50.0, 5.0)");

        // Product flow for stock
        connection.createStatement().execute("INSERT INTO product_flow (product_id, event_type, quantity, event_date) VALUES (1, 'PURCHASE', 50, '2025-06-01')");
        connection.createStatement().execute("INSERT INTO product_flow (product_id, event_type, quantity, event_date) VALUES (1, 'SALE', -5, '2025-06-01')");

        // Inventory status
        connection.createStatement().execute("INSERT INTO inventory_lots (product_id, quantity) VALUES (1, 3)"); // low stock
        connection.createStatement().execute("INSERT INTO inventory_lots (product_id, quantity) VALUES (2, 0)"); // out of stock
    }

    @Test
    void getSalesReportSummary_returnsCorrectAggregations() {
        Map<String, Object> summary = repository.getSalesReportSummary("2025-06-01", "2025-06-01", null);
        assertNotNull(summary);
        assertTrue(((BigDecimal) summary.get("total_sales")).compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void getTopSellingProducts_returnsRankedByQuantity() {
        List<Map<String, Object>> top = repository.getTopSellingProducts("2025-06-01", "2025-06-01", 5, null);
        assertFalse(top.isEmpty());
        Map<String, Object> first = top.get(0);
        assertEquals("LAP1", first.get("sku"));
    }

    @Test
    void getBusinessHealthOverview_includesStockCounts() {
        Map<String, Object> health = repository.getBusinessHealthOverview("2025-06-01", "2025-06-01");
        assertNotNull(health);
        assertTrue(((Number) health.get("out_of_stock_count")).intValue() >= 1);
        assertTrue(((Number) health.get("low_stock_count")).intValue() >= 1);
    }
}
