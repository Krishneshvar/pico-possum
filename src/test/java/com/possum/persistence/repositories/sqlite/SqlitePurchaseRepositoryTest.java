package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.PurchaseOrder;
import com.possum.domain.model.PurchaseOrderItem;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.PurchaseOrderFilter;
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

class SqlitePurchaseRepositoryTest {

    private Connection connection;
    private SqlitePurchaseRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqlitePurchaseRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("CREATE TABLE suppliers (id INTEGER PRIMARY KEY, name TEXT, deleted_at TEXT)");
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, sku TEXT)");
        connection.createStatement().execute("""
            CREATE TABLE purchase_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier_id INTEGER,
                invoice_number TEXT,
                payment_method_id INTEGER,
                status TEXT,
                order_date TEXT DEFAULT CURRENT_TIMESTAMP,
                received_date TEXT,
                created_by INTEGER
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE purchase_order_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                purchase_order_id INTEGER,
                product_id INTEGER,
                quantity INTEGER,
                unit_cost REAL,
                FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id),
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
        """);
        
        // Seed foreign key tables
        connection.createStatement().execute("INSERT INTO suppliers (id, name) VALUES (1, 'Jane Supplier')");
        connection.createStatement().execute("INSERT INTO payment_methods (id, name) VALUES (1, 'Cash')");
        connection.createStatement().execute("INSERT INTO users (id, name) VALUES (1, 'Admin')");
        connection.createStatement().execute("INSERT INTO products (id, name, sku) VALUES (1, 'Laptop', 'LAP1')");
    }

    @Test
    void createPurchaseOrder_insertsSuccessfully() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, "LAP1", "Laptop", null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));
        assertTrue(poId > 0);

        List<PurchaseOrderItem> items = repository.getPurchaseOrderItems(poId);
        assertEquals(1, items.size());
        assertEquals(5, items.get(0).quantity());
    }

    @Test
    void getPurchaseOrderById_returnsOrderWithAggregations() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, "LAP1", "Laptop", null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));

        Optional<PurchaseOrder> po = repository.getPurchaseOrderById(poId);
        assertTrue(po.isPresent());
        assertEquals("INV-100", po.get().invoiceNumber());
        assertEquals("Jane Supplier", po.get().supplierName());
        assertEquals(0, new BigDecimal("500.00").compareTo(po.get().totalCost()));
    }
}
