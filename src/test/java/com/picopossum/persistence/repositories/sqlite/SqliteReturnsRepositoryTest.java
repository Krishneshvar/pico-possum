package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.Return;
import com.picopossum.domain.model.ReturnItem;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ReturnFilter;
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

class SqliteReturnsRepositoryTest {

    private Connection connection;
    private SqliteReturnsRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteReturnsRepository(new com.picopossum.persistence.db.ConnectionProvider() {
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
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, sku TEXT)");
        connection.createStatement().execute("CREATE TABLE sales (id INTEGER PRIMARY KEY, invoice_number TEXT, invoice_id TEXT)");
        connection.createStatement().execute("CREATE TABLE sale_items (id INTEGER PRIMARY KEY, sale_id INTEGER, product_id INTEGER, price_per_unit REAL, cost_per_unit REAL)");
        connection.createStatement().execute("CREATE TABLE returns (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT UNIQUE, sale_id INTEGER, reason TEXT, refund_amount REAL, payment_method_id INTEGER, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        connection.createStatement().execute("CREATE TABLE return_items (id INTEGER PRIMARY KEY AUTOINCREMENT, return_id INTEGER, sale_item_id INTEGER, quantity INTEGER, refund_amount REAL, product_id INTEGER, price_per_unit REAL, sku TEXT, product_name TEXT)");
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT)");

        connection.createStatement().execute("INSERT INTO products (id, name, sku) VALUES (1, 'Product', 'SKU')");
        connection.createStatement().execute("INSERT INTO sales (id, invoice_number, invoice_id) VALUES (1, 'INV-100', 'INV-100')");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, product_id, price_per_unit, cost_per_unit) VALUES (1, 1, 1, 50.0, 40.0)");
    }

    @Test
    void insertReturn_insertsSuccessfully() {
        Return ret = new Return(null, 1L, "Defective", null, null, null, BigDecimal.ZERO, null, null, "RET-001");
        long id = repository.insertReturn(ret);
        assertTrue(id > 0);
    }

    @Test
    void insertReturnItem_insertsSuccessfully() {
        Return ret = new Return(null, 1L, "Defective", null, null, null, BigDecimal.ZERO, null, null, "RET-001");
        long returnId = repository.insertReturn(ret);

        ReturnItem item = new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), 1L, new BigDecimal("50.00"), "SKU", "Product");
        long itemId = repository.insertReturnItem(item);
        assertTrue(itemId > 0);
    }

    @Test
    void findReturnById_found_returnsReturnWithJoins() {
        long returnId = repository.insertReturn(new Return(null, 1L, "Defective", null, null, null, new BigDecimal("100.00"), null, null, "RET-001"));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), 1L, new BigDecimal("50.00"), "SKU", "Product"));

        Optional<Return> found = repository.findReturnById(returnId);
        assertTrue(found.isPresent());
        assertEquals("Defective", found.get().reason());
        assertEquals("INV-100", found.get().invoiceNumber());
        assertEquals(new BigDecimal("100.00").stripTrailingZeros(), found.get().totalRefund().stripTrailingZeros());
    }

    @Test
    void findReturnsBySaleId_found_returnsList() {
        repository.insertReturn(new Return(null, 1L, "Defective 1", null, null, null, new BigDecimal("100.00"), null, null, "RET-001"));
        repository.insertReturn(new Return(null, 1L, "Defective 2", null, null, null, new BigDecimal("50.00"), null, null, "RET-002"));

        List<Return> list = repository.findReturnsBySaleId(1L);
        assertEquals(2, list.size());
    }

    @Test
    void findReturnItems_returnsItemsWithMappedData() {
        long returnId = repository.insertReturn(new Return(null, 1L, "Defective", null, null, null, new BigDecimal("100.00"), null, null, "RET-001"));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), 1L, new BigDecimal("50.00"), "SKU", "Product"));

        List<ReturnItem> items = repository.findReturnItems(returnId);
        assertEquals(1, items.size());
        assertEquals(2, items.get(0).quantity());
        assertEquals("SKU", items.get(0).sku());
        assertEquals("Product", items.get(0).productName());
    }

    @Test
    void getTotalReturnedQuantity_returnsSum() {
        long returnId = repository.insertReturn(new Return(null, 1L, "Defective", null, null, null, BigDecimal.ZERO, null, null, "RET-001"));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), 1L, new BigDecimal("50.00"), "SKU", "Product"));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 3, new BigDecimal("150.00"), 1L, new BigDecimal("50.00"), "SKU", "Product"));

        int total = repository.getTotalReturnedQuantity(1L);
        assertEquals(5, total);
    }

    @Test
    void findReturns_withFilters_returnsCorrectResults() {
        long returnId1 = repository.insertReturn(new Return(null, 1L, "Damaged", null, null, null, new BigDecimal("100.00"), null, null, "RET-001"));
        repository.insertReturnItem(new ReturnItem(null, returnId1, 1L, 2, new BigDecimal("100.00"), 1L, new BigDecimal("50.00"), "SKU", "Product"));
        
        long returnId2 = repository.insertReturn(new Return(null, 1L, "Wrong item", null, null, null, new BigDecimal("50.00"), null, null, "RET-002"));
        repository.insertReturnItem(new ReturnItem(null, returnId2, 1L, 1, new BigDecimal("50.00"), 1L, new BigDecimal("50.00"), "SKU", "Product"));
        
        ReturnFilter all = new ReturnFilter(null, null, null, null, null, null, null, 1, 10, "created_at", "ASC");
        PagedResult<Return> allResult = repository.findReturns(all);
        assertEquals(2, allResult.totalCount());

        ReturnFilter byReason = new ReturnFilter(null, null, null, null, null, null, "Damaged", 1, 10, "created_at", "ASC");
        PagedResult<Return> reasonResult = repository.findReturns(byReason);
        assertEquals(1, reasonResult.totalCount());

        ReturnFilter byAmount = new ReturnFilter(null, null, null, new BigDecimal("90.00"), new BigDecimal("150.00"), null, null, 1, 10, "created_at", "ASC");
        PagedResult<Return> amountResult = repository.findReturns(byAmount);
        assertEquals(1, amountResult.totalCount());
        assertEquals("Damaged", amountResult.items().get(0).reason());
    }
}
