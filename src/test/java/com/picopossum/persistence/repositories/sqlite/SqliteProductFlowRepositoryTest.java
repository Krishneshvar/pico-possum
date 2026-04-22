package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.ProductFlow;
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
        repository = new SqliteProductFlowRepository(new com.picopossum.persistence.db.ConnectionProvider() {
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
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE sale_items (id INTEGER PRIMARY KEY, sale_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE sales (id INTEGER PRIMARY KEY, customer_id INTEGER, invoice_number TEXT, invoice_id TEXT, payment_method_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE returns (id INTEGER PRIMARY KEY, invoice_id TEXT, payment_method_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE return_items (id INTEGER PRIMARY KEY, return_id INTEGER, sale_item_id INTEGER, quantity INTEGER, refund_amount REAL, product_id INTEGER, price_per_unit REAL, sku TEXT, product_name TEXT)");
        connection.createStatement().execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY, sale_id INTEGER, type TEXT, status TEXT, payment_method_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("""
            CREATE TABLE product_flow (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER,
                event_type TEXT,
                quantity INTEGER,
                reference_type TEXT,
                reference_id INTEGER,
                event_date TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);

        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product 1')");
    }

    @Test
    void insertProductFlow_insertsSuccessfully() {
        ProductFlow flow = new ProductFlow(null, 1L, "adjustment", 10, "inv_adj", 100L, null, null, null, null, null, null);
        long id = repository.insertProductFlow(flow);
        assertTrue(id > 0);
    }

    @Test
    void findFlowByProductId_returnsMappedFlows() {
        ProductFlow flow = new ProductFlow(null, 1L, "adjustment", 10, "inv_adj", 100L, null, null, null, null, null, null);
        repository.insertProductFlow(flow);

        List<ProductFlow> list = repository.findFlowByProductId(1L, 10, 0, null, null, null);
        assertEquals(1, list.size());
        assertEquals("Product 1", list.get(0).productName());
        assertEquals(10, list.get(0).quantity());
    }

    @Test
    void getProductFlowSummary_calculatesCorrectly() {
        // Replacement for purchase: use adjustment (gained)
        repository.insertProductFlow(new ProductFlow(null, 1L, "adjustment", 20, "manual", 10L, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "sale", -5, "sale_item", 1L, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "return", 2, "return_item", 1L, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "adjustment", -1, "inv_adj", 1L, null, null, null, null, null, null));
        repository.insertProductFlow(new ProductFlow(null, 1L, "adjustment", 5, "inv_adj", 2L, null, null, null, null, null, null));

        Map<String, Object> summary = repository.getProductFlowSummary(1L);

        assertEquals(5, summary.get("totalSold"));
        assertEquals(2, summary.get("totalReturned"));
        assertEquals(1, summary.get("totalLost"));
        assertEquals(25, summary.get("totalGained")); // 20 (original po) + 5
        assertEquals(5, summary.get("totalEvents"));
        assertEquals(21, summary.get("netMovement")); // 2 + 25 - 5 - 1 = 21
    }
}
