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
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE variants (id INTEGER PRIMARY KEY, product_id INTEGER, name TEXT, sku TEXT, FOREIGN KEY(product_id) REFERENCES products(id))");
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
                variant_id INTEGER,
                quantity INTEGER,
                unit_cost REAL,
                FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id),
                FOREIGN KEY(variant_id) REFERENCES variants(id)
            )
        """);
        
        // Seed foreign key tables
        connection.createStatement().execute("INSERT INTO suppliers (id, name) VALUES (1, 'Jane Supplier')");
        connection.createStatement().execute("INSERT INTO payment_methods (id, name) VALUES (1, 'Cash')");
        connection.createStatement().execute("INSERT INTO users (id, name) VALUES (1, 'Admin')");
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Laptop')");
        connection.createStatement().execute("INSERT INTO variants (id, product_id, name, sku) VALUES (1, 1, 'Standard', 'LAP1')");
    }

    @Test
    void createPurchaseOrder_insertsSuccessfully() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, null, null, null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));
        assertTrue(poId > 0);

        List<PurchaseOrderItem> items = repository.getPurchaseOrderItems(poId);
        assertEquals(1, items.size());
        assertEquals(5, items.get(0).quantity());
        assertEquals(0, new BigDecimal("100.00").compareTo(items.get(0).unitCost()));
    }

    @Test
    void createPurchaseOrder_invalidSupplier_throwsException() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, null, null, null, 5, new BigDecimal("100.00"));
        assertThrows(IllegalStateException.class, () -> repository.createPurchaseOrder(99L, "INV-100", 1L, 1L, List.of(item)));
    }

    @Test
    void createPurchaseOrder_invalidVariant_throwsException() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 99L, null, null, null, 5, new BigDecimal("100.00"));
        assertThrows(IllegalStateException.class, () -> repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item)));
    }

    @Test
    void getPurchaseOrderById_returnsOrderWithAggregations() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, null, null, null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));

        Optional<PurchaseOrder> po = repository.getPurchaseOrderById(poId);
        assertTrue(po.isPresent());
        assertEquals("INV-100", po.get().invoiceNumber());
        assertEquals("Jane Supplier", po.get().supplierName());
        assertEquals("Cash", po.get().paymentMethodName());
        assertEquals("Admin", po.get().createdByName());
        assertEquals(1, po.get().itemCount());
        assertEquals(0, new BigDecimal("500.00").compareTo(po.get().totalCost()));
    }

    @Test
    void updatePurchaseOrder_updatesSuccessfully() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, null, null, null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));

        PurchaseOrderItem newItem = new PurchaseOrderItem(null, null, 1L, null, null, null, 10, new BigDecimal("80.00"));
        boolean success = repository.updatePurchaseOrder(poId, 1L, 1L, List.of(newItem));
        assertTrue(success);

        List<PurchaseOrderItem> items = repository.getPurchaseOrderItems(poId);
        assertEquals(1, items.size());
        assertEquals(10, items.get(0).quantity());
        assertEquals(0, new BigDecimal("80.00").compareTo(items.get(0).unitCost()));

        Optional<PurchaseOrder> po = repository.getPurchaseOrderById(poId);
        assertEquals(0, new BigDecimal("800.00").compareTo(po.get().totalCost()));
    }

    @Test
    void updatePurchaseOrder_notPending_throwsException() throws SQLException {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, null, null, null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));

        repository.receivePurchaseOrder(poId, 1L); // Sets to received

        assertThrows(IllegalStateException.class, () -> repository.updatePurchaseOrder(poId, 1L, 1L, List.of(item)));
    }

    @Test
    void receivePurchaseOrder_changesStatus() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, null, null, null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));

        boolean result = repository.receivePurchaseOrder(poId, 1L);
        assertTrue(result);

        Optional<PurchaseOrder> po = repository.getPurchaseOrderById(poId);
        assertEquals("received", po.get().status());
        assertNotNull(po.get().receivedDate());
    }

    @Test
    void cancelPurchaseOrder_changesStatus() {
        PurchaseOrderItem item = new PurchaseOrderItem(null, null, 1L, null, null, null, 5, new BigDecimal("100.00"));
        long poId = repository.createPurchaseOrder(1L, "INV-100", 1L, 1L, List.of(item));

        int updated = repository.cancelPurchaseOrder(poId);
        assertEquals(1, updated);

        Optional<PurchaseOrder> po = repository.getPurchaseOrderById(poId);
        assertEquals("cancelled", po.get().status());
    }

    @Test
    void getAllPurchaseOrders_filtering_returnsCorrectlyFilteredResults() {
        PurchaseOrderItem item1 = new PurchaseOrderItem(null, null, 1L, null, null, null, 1, new BigDecimal("50.00"));
        long po1 = repository.createPurchaseOrder(1L, "INV-A", 1L, 1L, List.of(item1));
        
        PurchaseOrderItem item2 = new PurchaseOrderItem(null, null, 1L, null, null, null, 10, new BigDecimal("50.00"));
        long po2 = repository.createPurchaseOrder(1L, "INV-B", 1L, 1L, List.of(item2));

        repository.receivePurchaseOrder(po2, 1L);

        PurchaseOrderFilter all = new PurchaseOrderFilter(1, 10, null, null, null, null, "order_date", "ASC", null, null, null);
        assertEquals(2, repository.getAllPurchaseOrders(all).totalCount());

        PurchaseOrderFilter search = new PurchaseOrderFilter(1, 10, "INV-A", null, null, null, "order_date", "ASC", null, null, null);
        assertEquals(1, repository.getAllPurchaseOrders(search).totalCount());

        PurchaseOrderFilter prices = new PurchaseOrderFilter(1, 10, null, null, null, null, "order_date", "ASC", null, new BigDecimal("400"), null);
        assertEquals(1, repository.getAllPurchaseOrders(prices).totalCount()); // only po2 has totalCost=500
        
        PurchaseOrderFilter status = new PurchaseOrderFilter(1, 10, null, List.of("received"), null, null, "order_date", "ASC", null, null, null);
        assertEquals(1, repository.getAllPurchaseOrders(status).totalCount());
    }
}
