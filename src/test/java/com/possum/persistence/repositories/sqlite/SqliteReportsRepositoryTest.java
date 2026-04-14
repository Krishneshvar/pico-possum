package com.possum.persistence.repositories.sqlite;

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
        repository = new SqliteReportsRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("CREATE TABLE categories (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY, 
                name TEXT, 
                category_id INTEGER, 
                sku TEXT, 
                mrp REAL, 
                cost_price REAL, 
                stock_alert_cap INTEGER DEFAULT 10, 
                status TEXT DEFAULT 'active'
            )
        """);
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("""
            CREATE TABLE sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                invoice_number TEXT,
                status TEXT DEFAULT 'completed',
                total_amount REAL DEFAULT 0,
                paid_amount REAL DEFAULT 0,
                total_tax REAL DEFAULT 0,
                discount REAL DEFAULT 0,
                sale_date TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE sale_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_id INTEGER,
                product_id INTEGER,
                quantity INTEGER,
                price_per_unit REAL,
                cost_per_unit REAL DEFAULT 0,
                tax_amount REAL DEFAULT 0,
                discount_amount REAL DEFAULT 0
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_id INTEGER,
                type TEXT,
                status TEXT,
                amount REAL,
                payment_method_id INTEGER,
                transaction_date TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE legacy_sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                net_amount REAL,
                payment_method_id INTEGER,
                payment_method_name TEXT,
                sale_date TEXT,
                invoice_number TEXT,
                customer_name TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_lots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER,
                quantity INTEGER
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE product_flow (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER,
                event_type TEXT,
                quantity INTEGER,
                event_date TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    private void seedData() throws SQLException {
        connection.createStatement().execute("INSERT INTO payment_methods VALUES (1, 'Cash')");
        connection.createStatement().execute("INSERT INTO payment_methods VALUES (2, 'UPI')");
        connection.createStatement().execute("INSERT INTO customers VALUES (1, 'John Doe')");
        connection.createStatement().execute("INSERT INTO categories VALUES (1, 'Electronics')");
        
        // Products with MRP/Cost
        connection.createStatement().execute("INSERT INTO products (id, name, category_id, sku, mrp, cost_price, stock_alert_cap) VALUES (1, 'Laptop', 1, 'LAP1', 1000.0, 600.0, 5)");
        connection.createStatement().execute("INSERT INTO products (id, name, category_id, sku, mrp, cost_price, stock_alert_cap) VALUES (2, 'Phone', 1, 'PHO1', 500.0, 300.0, 5)");

        // Sale 1
        connection.createStatement().execute("INSERT INTO sales (id, customer_id, invoice_number, status, total_amount, paid_amount, total_tax, discount, sale_date) VALUES (1, 1, 'INV-001', 'completed', 1000.0, 1000.0, 50.0, 10.0, '2025-06-01 10:00:00')");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, product_id, quantity, price_per_unit, cost_per_unit, tax_amount, discount_amount) VALUES (1, 1, 1, 3, 300.0, 200.0, 15.0, 5.0)");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, product_id, quantity, price_per_unit, cost_per_unit, tax_amount, discount_amount) VALUES (2, 1, 2, 1, 100.0, 50.0, 5.0, 5.0)");

        // Transaction 1
        connection.createStatement().execute("INSERT INTO transactions (id, sale_id, type, status, amount, payment_method_id, transaction_date) VALUES (1, 1, 'payment', 'completed', 1000.0, 1, '2025-06-01 10:00:00')");

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
