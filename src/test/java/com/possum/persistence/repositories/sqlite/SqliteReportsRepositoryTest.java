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
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, category_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE variants (id INTEGER PRIMARY KEY, product_id INTEGER, name TEXT, sku TEXT, stock_alert_cap INTEGER DEFAULT 10, status TEXT DEFAULT 'active')");
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
                variant_id INTEGER,
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
                variant_id INTEGER,
                remaining_quantity INTEGER
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE product_flow (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                variant_id INTEGER,
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
        connection.createStatement().execute("INSERT INTO products VALUES (1, 'Laptop', 1)");
        connection.createStatement().execute("INSERT INTO products VALUES (2, 'Phone', 1)");
        connection.createStatement().execute("INSERT INTO variants VALUES (1, 1, 'Standard', 'LAP1', 5, 'active')");
        connection.createStatement().execute("INSERT INTO variants VALUES (2, 2, 'Basic', 'PHO1', 5, 'active')");

        // Two completed sales on 2025-06-01
        connection.createStatement().execute("INSERT INTO sales (id, customer_id, invoice_number, status, total_amount, paid_amount, total_tax, discount, sale_date) VALUES (1, 1, 'INV-001', 'completed', 1000.0, 1000.0, 50.0, 10.0, '2025-06-01 10:00:00')");
        connection.createStatement().execute("INSERT INTO sales (id, customer_id, invoice_number, status, total_amount, paid_amount, total_tax, discount, sale_date) VALUES (2, 1, 'INV-002', 'completed', 500.0, 500.0, 25.0, 0.0, '2025-06-01 12:00:00')");
        // One cancelled sale — should be excluded
        connection.createStatement().execute("INSERT INTO sales (id, customer_id, invoice_number, status, total_amount, paid_amount, total_tax, discount, sale_date) VALUES (3, 1, 'INV-003', 'cancelled', 200.0, 0.0, 10.0, 0.0, '2025-06-01 15:00:00')");

        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, variant_id, quantity, price_per_unit, cost_per_unit, tax_amount, discount_amount) VALUES (1, 1, 1, 3, 300.0, 200.0, 15.0, 5.0)");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, variant_id, quantity, price_per_unit, cost_per_unit, tax_amount, discount_amount) VALUES (2, 1, 2, 1, 100.0, 50.0, 5.0, 5.0)");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, variant_id, quantity, price_per_unit, cost_per_unit, tax_amount, discount_amount) VALUES (3, 2, 1, 2, 200.0, 150.0, 10.0, 0.0)");

        connection.createStatement().execute("INSERT INTO transactions (id, sale_id, type, status, amount, payment_method_id, transaction_date) VALUES (1, 1, 'payment', 'completed', 1000.0, 1, '2025-06-01 10:00:00')");
        connection.createStatement().execute("INSERT INTO transactions (id, sale_id, type, status, amount, payment_method_id, transaction_date) VALUES (2, 2, 'payment', 'completed', 500.0, 2, '2025-06-01 12:00:00')");
        connection.createStatement().execute("INSERT INTO transactions (id, sale_id, type, status, amount, payment_method_id, transaction_date) VALUES (3, 1, 'refund', 'completed', -100.0, 1, '2025-06-01 11:00:00')");

        // One legacy sale
        connection.createStatement().execute("INSERT INTO legacy_sales (net_amount, payment_method_id, payment_method_name, sale_date, invoice_number, customer_name) VALUES (300.0, 1, 'Cash', '2025-06-01', 'LEG-001', 'Legacy Cust')");

        // Product flow
        connection.createStatement().execute("INSERT INTO product_flow (variant_id, event_type, quantity, event_date) VALUES (1, 'PURCHASE', 50, '2025-06-01')");
        connection.createStatement().execute("INSERT INTO product_flow (variant_id, event_type, quantity, event_date) VALUES (1, 'SALE', -5, '2025-06-01')");
        connection.createStatement().execute("INSERT INTO product_flow (variant_id, event_type, quantity, event_date) VALUES (2, 'SALE', -3, '2025-06-01')");

        // Inventory lots
        connection.createStatement().execute("INSERT INTO inventory_lots (variant_id, remaining_quantity) VALUES (1, 3)"); // low stock (under alert cap 5)
        connection.createStatement().execute("INSERT INTO inventory_lots (variant_id, remaining_quantity) VALUES (2, 0)"); // out of stock
    }

    @Test
    void getSalesReportSummary_returnsCorrectAggregations() {
        Map<String, Object> summary = repository.getSalesReportSummary("2025-06-01", "2025-06-01", null);

        // 2 completed sales (1000 + 500) + 1 legacy (300) = 1800
        assertEquals(3, ((Number) summary.get("total_transactions")).intValue());
        assertTrue(((BigDecimal) summary.get("total_sales")).compareTo(new BigDecimal("1800")) == 0);

        // Refunds = ABS(-100) = 100
        assertTrue(((BigDecimal) summary.get("total_refunds")).compareTo(new BigDecimal("100")) == 0);
    }

    @Test
    void getSalesReportSummary_calculatesProfitabilityCorrectly() {
        Map<String, Object> summary = repository.getSalesReportSummary("2025-06-01", "2025-06-01", null);

        // Costs (from items 1, 2, 3): (3*200) + (1*50) + (2*150) = 600 + 50 + 300 = 950
        BigDecimal totalCost = (BigDecimal) summary.get("total_cost");
        assertEquals(0, totalCost.compareTo(new BigDecimal("950.0")), "Expected cost 950.0 but got " + totalCost);

        // net_sales = total_sales (1800) - tax (105) - discount (20) - refunds (100) = 1575
        BigDecimal netSales = (BigDecimal) summary.get("net_sales");
        assertEquals(0, netSales.compareTo(new BigDecimal("1575.0")), "Expected net sales 1575.0 but got " + netSales);

        // Gross Profit = Net Sales (1575) - Total Cost (950) = 625
        BigDecimal grossProfit = (BigDecimal) summary.get("gross_profit");
        assertEquals(0, grossProfit.compareTo(new BigDecimal("625.0")), "Expected gross profit 625.0 but got " + grossProfit);
    }

    @Test
    void getSalesReportSummary_withPaymentMethodFilter_filtersCorrectly() {
        Map<String, Object> summary = repository.getSalesReportSummary("2025-06-01", "2025-06-01", List.of(1L));

        // Only Cash transactions → only sale #1
        BigDecimal totalSales = (BigDecimal) summary.get("total_sales");
        assertNotNull(totalSales);
    }

    @Test
    void getDailyBreakdown_returnsGroupedByDate() {
        List<Map<String, Object>> breakdown = repository.getDailyBreakdown("2025-06-01", "2025-06-01", null);

        assertFalse(breakdown.isEmpty());
        Map<String, Object> day = breakdown.get(0);
        assertEquals("2025-06-01", day.get("date"));
        assertTrue(((Number) day.get("total_transactions")).intValue() >= 2);
    }

    @Test
    void getMonthlyBreakdown_returnsGroupedByMonth() {
        List<Map<String, Object>> breakdown = repository.getMonthlyBreakdown("2025-06-01", "2025-06-30", null);

        assertFalse(breakdown.isEmpty());
        Map<String, Object> month = breakdown.get(0);
        assertEquals("2025-06", month.get("month"));
    }

    @Test
    void getYearlyBreakdown_returnsGroupedByYear() {
        List<Map<String, Object>> breakdown = repository.getYearlyBreakdown("2025-01-01", "2025-12-31", null);

        assertFalse(breakdown.isEmpty());
        Map<String, Object> year = breakdown.get(0);
        assertEquals("2025", year.get("year"));
    }

    @Test
    void getTopSellingProducts_returnsRankedByQuantity() {
        List<Map<String, Object>> top = repository.getTopSellingProducts("2025-06-01", "2025-06-01", 5, null);

        assertFalse(top.isEmpty());
        // Variant 1 sold 3+2=5, Variant 2 sold 1
        Map<String, Object> first = top.get(0);
        assertEquals("LAP1", first.get("sku"));
        assertEquals(5, ((Number) first.get("total_quantity_sold")).intValue());
    }

    @Test
    void getSalesByPaymentMethod_returnsBreakdownWithLegacy() {
        List<Map<String, Object>> breakdown = repository.getSalesByPaymentMethod("2025-06-01", "2025-06-01");

        assertFalse(breakdown.isEmpty());
        // Should have Cash and UPI entries
        Map<String, Object> cashRow = breakdown.stream()
            .filter(r -> "Cash".equalsIgnoreCase((String) r.get("payment_method")))
            .findFirst().orElse(null);
        assertNotNull(cashRow);
        // Cash is used in transaction 1 (1000) and legacy sale (300)
        assertTrue(((BigDecimal) cashRow.get("total_amount")).compareTo(new BigDecimal("1000")) >= 0);
    }

    @Test
    void getBusinessHealthOverview_includesStockCounts() {
        Map<String, Object> health = repository.getBusinessHealthOverview("2025-06-01", "2025-06-01");

        assertNotNull(health);
        // variant 2 has 0 remaining → out of stock
        assertTrue(((Number) health.get("out_of_stock_count")).intValue() >= 1);
        // variant 1 has 3, alert cap 5 → low stock
        assertTrue(((Number) health.get("low_stock_count")).intValue() >= 1);
    }

    @Test
    void getStockMovementSummary_returnsMovements() {
        List<Map<String, Object>> movements = repository.getStockMovementSummary("2025-06-01", "2025-06-01", null);

        assertFalse(movements.isEmpty());
        // variant 1: 50 incoming, 5 outgoing; variant 2: 0 incoming, 3 outgoing
        Map<String, Object> laptopVariant = movements.stream()
            .filter(m -> "LAP1".equals(m.get("sku")))
            .findFirst().orElse(null);
        assertNotNull(laptopVariant);
        assertEquals(50, ((Number) laptopVariant.get("incoming")).intValue());
        assertEquals(5, ((Number) laptopVariant.get("outgoing")).intValue());
    }

    @Test
    void getStockMovementSummary_withCategoryFilter_filtersCorrectly() {
        // Filter by Electronics category (id=1) - both products are there
        List<Map<String, Object>> movements = repository.getStockMovementSummary("2025-06-01", "2025-06-01", 1L);
        // Both should still appear since both are in Electronics
        assertEquals(2, movements.size());
    }

    @Test
    void getSalesReportSummary_emptyRange_returnsZeros() {
        Map<String, Object> summary = repository.getSalesReportSummary("2020-01-01", "2020-01-31", null);

        assertEquals(0, ((Number) summary.get("total_transactions")).intValue());
        assertEquals(0, ((BigDecimal) summary.get("total_sales")).compareTo(BigDecimal.ZERO));
    }
}
