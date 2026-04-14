package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Return;
import com.possum.domain.model.ReturnItem;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ReturnFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
        repository = new SqliteReturnsRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE variants (id INTEGER PRIMARY KEY, product_id INTEGER, name TEXT, sku TEXT)");
        connection.createStatement().execute("CREATE TABLE sales (id INTEGER PRIMARY KEY, invoice_number TEXT)");
        connection.createStatement().execute("CREATE TABLE sale_items (id INTEGER PRIMARY KEY, sale_id INTEGER, variant_id INTEGER, price_per_unit REAL, tax_rate REAL)");
        connection.createStatement().execute("CREATE TABLE returns (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER, user_id INTEGER, reason TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        connection.createStatement().execute("CREATE TABLE return_items (id INTEGER PRIMARY KEY AUTOINCREMENT, return_id INTEGER, sale_item_id INTEGER, quantity INTEGER, refund_amount REAL)");
        connection.createStatement().execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY, sale_id INTEGER, type TEXT, payment_method_id INTEGER)");
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT)");

        connection.createStatement().execute("INSERT INTO users (id, name) VALUES (1, 'Admin')");
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        connection.createStatement().execute("INSERT INTO variants (id, product_id, name, sku) VALUES (1, 1, 'Var', 'SKU')");
        connection.createStatement().execute("INSERT INTO sales (id, invoice_number) VALUES (1, 'INV-100')");
        connection.createStatement().execute("INSERT INTO sale_items (id, sale_id, variant_id, price_per_unit, tax_rate) VALUES (1, 1, 1, 50.0, 5.0)");
    }

    @Test
    void insertReturn_insertsSuccessfully() {
        Return ret = new Return(null, 1L, 1L, "Defective", null, null, null, null, null, null);
        long id = repository.insertReturn(ret);
        assertTrue(id > 0);
    }

    @Test
    void insertReturnItem_insertsSuccessfully() {
        Return ret = new Return(null, 1L, 1L, "Defective", null, null, null, null, null, null);
        long returnId = repository.insertReturn(ret);

        ReturnItem item = new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), null, null, null, null, null, null);
        long itemId = repository.insertReturnItem(item);
        assertTrue(itemId > 0);
    }

    @Test
    void findReturnById_found_returnsReturnWithJoins() {
        long returnId = repository.insertReturn(new Return(null, 1L, 1L, "Defective", null, null, null, null, null, null));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), null, null, null, null, null, null));

        Optional<Return> found = repository.findReturnById(returnId);
        assertTrue(found.isPresent());
        assertEquals("Defective", found.get().reason());
        assertEquals("INV-100", found.get().invoiceNumber());
        assertEquals("Admin", found.get().processedByName());
        assertEquals(new BigDecimal("100.00").stripTrailingZeros(), found.get().totalRefund().stripTrailingZeros());
    }

    @Test
    void findReturnsBySaleId_found_returnsList() {
        long returnId1 = repository.insertReturn(new Return(null, 1L, 1L, "Defective 1", null, null, null, null, null, null));
        long returnId2 = repository.insertReturn(new Return(null, 1L, 1L, "Defective 2", null, null, null, null, null, null));

        List<Return> list = repository.findReturnsBySaleId(1L);
        assertEquals(2, list.size());
    }

    @Test
    void findReturnItems_returnsItemsWithMappedData() {
        long returnId = repository.insertReturn(new Return(null, 1L, 1L, "Defective", null, null, null, null, null, null));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), null, null, null, null, null, null));

        List<ReturnItem> items = repository.findReturnItems(returnId);
        assertEquals(1, items.size());
        assertEquals(2, items.get(0).quantity());
        assertEquals("Var", items.get(0).variantName());
        assertEquals("SKU", items.get(0).sku());
        assertEquals("Product", items.get(0).productName());
    }

    @Test
    void getTotalReturnedQuantity_returnsSum() {
        long returnId = repository.insertReturn(new Return(null, 1L, 1L, "Defective", null, null, null, null, null, null));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 2, new BigDecimal("100.00"), null, null, null, null, null, null));
        repository.insertReturnItem(new ReturnItem(null, returnId, 1L, 3, new BigDecimal("150.00"), null, null, null, null, null, null));

        int total = repository.getTotalReturnedQuantity(1L);
        assertEquals(5, total);
    }

    @Test
    void findReturns_withFilters_returnsCorrectResults() {
        long returnId1 = repository.insertReturn(new Return(null, 1L, 1L, "Damaged", null, null, null, null, null, null));
        repository.insertReturnItem(new ReturnItem(null, returnId1, 1L, 2, new BigDecimal("100.00"), null, null, null, null, null, null));
        
        long returnId2 = repository.insertReturn(new Return(null, 1L, 1L, "Wrong item", null, null, null, null, null, null));
        repository.insertReturnItem(new ReturnItem(null, returnId2, 1L, 1, new BigDecimal("50.00"), null, null, null, null, null, null));
        
        ReturnFilter all = new ReturnFilter(null, null, null, null, null, null, null, null, 1, 10, "r.created_at", "ASC");
        PagedResult<Return> allResult = repository.findReturns(all);
        assertEquals(2, allResult.totalCount());

        ReturnFilter byReason = new ReturnFilter(null, null, null, null, null, null, null, "Damaged", 1, 10, "r.created_at", "ASC");
        PagedResult<Return> reasonResult = repository.findReturns(byReason);
        assertEquals(1, reasonResult.totalCount());

        ReturnFilter byAmount = new ReturnFilter(null, null, null, null, new BigDecimal("90.00"), new BigDecimal("150.00"), null, null, 1, 10, "r.created_at", "ASC");
        PagedResult<Return> amountResult = repository.findReturns(byAmount);
        assertEquals(1, amountResult.totalCount());
        assertEquals("Damaged", amountResult.items().get(0).reason());
    }
}
