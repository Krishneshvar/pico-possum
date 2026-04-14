package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.ProductFlow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqliteProductFlowRepositoryTest {

    private Connection connection;
    private SqliteProductFlowRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteProductFlowRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE variants (id INTEGER PRIMARY KEY, product_id INTEGER, name TEXT)");
        connection.createStatement().execute("CREATE TABLE sale_items (id INTEGER PRIMARY KEY, sale_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE sales (id INTEGER PRIMARY KEY, customer_id INTEGER, invoice_number TEXT)");
        connection.createStatement().execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE purchase_orders (id INTEGER PRIMARY KEY, supplier_id INTEGER, invoice_number TEXT)");
        connection.createStatement().execute("CREATE TABLE suppliers (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY, sale_id INTEGER, type TEXT, status TEXT, payment_method_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("""
            CREATE TABLE product_flow (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                variant_id INTEGER,
                event_type TEXT,
                quantity INTEGER,
                reference_type TEXT,
                reference_id INTEGER,
                event_date TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);

        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product 1')");
        connection.createStatement().execute("INSERT INTO variants (id, product_id, name) VALUES (1, 1, 'Variant 1')");
        connection.createStatement().execute("INSERT INTO variants (id, product_id, name) VALUES (2, 1, 'Variant 2')");
    }

    @Test
    void insertProductFlow_insertsSuccessfully() {
        ProductFlow flow = new ProductFlow(null, 1L, "purchase", 10, "purchase_order", 100L, null, null, null, null, null, null, null);
        long id = repository.insertProductFlow(flow);
        assertTrue(id > 0);
    }

    @Test
    void findFlowByVariantId_returnsMappedFlows() {
        ProductFlow flow = new ProductFlow(null, 1L, "purchase", 10, "purchase_order", 100L, null, null, null, null, null, null, null);
        repository.insertProductFlow(flow);

        List<ProductFlow> list = repository.findFlowByVariantId(1L, 10, 0, null, null, null);
        assertEquals(1, list.size());
        assertEquals("Variant 1", list.get(0).variantName());
        assertEquals("Product 1", list.get(0).productName());
        assertEquals(10, list.get(0).quantity());
    }

    @Test
    void findFlowByProductId_returnsFlowsForAllVariants() {
        repository.insertProductFlow(new ProductFlow(null, 1L, "purchase", 10, "po", 1L, null, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 2L, "sale", -5, "sale_item", 1L, null, null, null, null, null, null, null));

        List<ProductFlow> list = repository.findFlowByProductId(1L, 10, 0, null, null, null);
        assertEquals(2, list.size());
    }

    @Test
    void getFlowSummary_calculatesCorrectly() {
        repository.insertProductFlow(new ProductFlow(null, 1L, "purchase", 20, "po", 1L, null, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "sale", -5, "sale_item", 1L, null, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "return", 2, "return_item", 1L, null, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "adjustment", -1, "inv_adj", 1L, null, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "adjustment", 5, "inv_adj", 2L, null, null, null, null, null, null, null));

        Map<String, Object> summary = repository.getFlowSummary(1L);

        assertEquals(20, summary.get("totalPurchased"));
        assertEquals(5, summary.get("totalSold"));
        assertEquals(2, summary.get("totalReturned"));
        assertEquals(1, summary.get("totalLost")); // negative adjustment = lost
        assertEquals(5, summary.get("totalGained")); // positive adjustment = gained
        assertEquals(5, summary.get("totalEvents"));
        assertEquals(21, summary.get("netMovement")); // 20 + 2 + 5 - 5 - 1 = 21
    }

    @Test
    void getProductFlowSummary_calculatesCorrectlyAcrossVariants() {
        repository.insertProductFlow(new ProductFlow(null, 1L, "purchase", 10, "po", 1L, null, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 2L, "sale", -5, "sale_item", 1L, null, null, null, null, null, null, null));

        Map<String, Object> summary = repository.getProductFlowSummary(1L);

        assertEquals(10, summary.get("totalPurchased"));
        assertEquals(5, summary.get("totalSold"));
        assertEquals(0, summary.get("totalReturned"));
        assertEquals(0, summary.get("totalLost"));
        assertEquals(0, summary.get("totalGained"));
        assertEquals(2, summary.get("totalEvents"));
        assertEquals(5, summary.get("netMovement"));
    }

    @Test
    void findFlowByReference_returnsExactMatch() {
        repository.insertProductFlow(new ProductFlow(null, 1L, "purchase", 10, "po", 999L, null, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "purchase", 5, "po", 888L, null, null, null, null, null, null, null));

        List<ProductFlow> list = repository.findFlowByReference("po", 999L);
        
        assertEquals(1, list.size());
        assertEquals(10, list.get(0).quantity());
        assertNull(list.get(0).variantName()); // The specific method explicitly maps NULLs for aggregates
    }
}
