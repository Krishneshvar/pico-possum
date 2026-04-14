package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Transaction;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;
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

class SqliteTransactionRepositoryTest {

    private Connection connection;
    private SqliteTransactionRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteTransactionRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("CREATE TABLE payment_methods (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE suppliers (id INTEGER PRIMARY KEY, name TEXT)");
        connection.createStatement().execute("CREATE TABLE sales (id INTEGER PRIMARY KEY, customer_id INTEGER, invoice_number TEXT)");
        connection.createStatement().execute("CREATE TABLE purchase_orders (id INTEGER PRIMARY KEY, supplier_id INTEGER, invoice_number TEXT)");
        connection.createStatement().execute("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_id INTEGER,
                purchase_order_id INTEGER,
                amount REAL,
                type TEXT,
                payment_method_id INTEGER,
                status TEXT,
                transaction_date TEXT
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
        
        connection.createStatement().execute("INSERT INTO payment_methods (id, name) VALUES (1, 'Cash')");
        connection.createStatement().execute("INSERT INTO customers (id, name) VALUES (1, 'John Doe')");
        connection.createStatement().execute("INSERT INTO suppliers (id, name) VALUES (1, 'Jane Supplier')");
        connection.createStatement().execute("INSERT INTO sales (id, customer_id, invoice_number) VALUES (1, 1, 'INV-100')");
        connection.createStatement().execute("INSERT INTO purchase_orders (id, supplier_id, invoice_number) VALUES (1, 1, 'PO-100')");
    }

    @Test
    void insertTransaction_insertsSuccessfully() {
        Transaction transaction = new Transaction(null, new BigDecimal("150.00"), "payment", 1L, null, "completed", LocalDateTime.now(), null, null, null);
        long id = repository.insertTransaction(transaction, 1L, null);
        assertTrue(id > 0);
    }

    @Test
    void findTransactionById_found_returnsUnifiedTransaction() {
        Transaction transaction = new Transaction(null, new BigDecimal("150.00"), "payment", 1L, null, "completed", LocalDateTime.now(), null, null, null);
        long id = repository.insertTransaction(transaction, 1L, null);

        Optional<Transaction> result = repository.findTransactionById(id);
        assertTrue(result.isPresent());
        assertEquals("payment", result.get().type());
        assertEquals("INV-100", result.get().invoiceNumber());
        assertEquals("John Doe", result.get().customerName());
        assertEquals("Cash", result.get().paymentMethodName());
    }

    @Test
    void findTransactionsByPurchaseOrderId_filtering_returnsList() {
        Transaction t1 = new Transaction(null, new BigDecimal("500.00"), "expense", 1L, null, "completed", LocalDateTime.now(), null, null, null);
        repository.insertTransaction(t1, null, 1L);

        List<Transaction> result = repository.findTransactionsByPurchaseOrderId(1L);
        assertEquals(1, result.size());
        assertEquals("PO-100", result.get(0).invoiceNumber());
        assertEquals("Jane Supplier", result.get(0).supplierName());
    }

    @Test
    void getTotalRefundedForSale_returnsSum() {
        repository.insertTransaction(new Transaction(null, new BigDecimal("-50.00"), "refund", 1L, null, "completed", LocalDateTime.now(), null, null, null), 1L, null);
        repository.insertTransaction(new Transaction(null, new BigDecimal("-20.00"), "refund", 1L, null, "completed", LocalDateTime.now(), null, null, null), 1L, null);
        repository.insertTransaction(new Transaction(null, new BigDecimal("100.00"), "payment", 1L, null, "completed", LocalDateTime.now(), null, null, null), 1L, null); // should not be counted
        
        BigDecimal total = repository.getTotalRefundedForSale(1L);
        assertEquals(0, new BigDecimal("70").compareTo(total)); // ABS(-50) + ABS(-20)
    }

    @Test
    void getTotalPaidForSale_returnsSum() {
        repository.insertTransaction(new Transaction(null, new BigDecimal("100.00"), "payment", 1L, null, "completed", LocalDateTime.now(), null, null, null), 1L, null);
        repository.insertTransaction(new Transaction(null, new BigDecimal("50.00"), "payment", 1L, null, "completed", LocalDateTime.now(), null, null, null), 1L, null);
        
        BigDecimal total = repository.getTotalPaidForSale(1L);
        assertEquals(0, new BigDecimal("150").compareTo(total));
    }

    @Test
    void findTransactions_withVariousFilters_returnsCorrectlyFilteredResults() throws SQLException {
        // Insert with space-separated timestamps so they match the WHERE clause string comparison
        connection.createStatement().execute("INSERT INTO transactions (sale_id, amount, type, payment_method_id, status, transaction_date) VALUES (1, 100.0, 'payment', 1, 'completed', '2025-05-20 10:00:00')");
        connection.createStatement().execute("INSERT INTO transactions (sale_id, amount, type, payment_method_id, status, transaction_date) VALUES (1, -50.0, 'refund', 1, 'completed', '2025-05-21 10:00:00')");
        connection.createStatement().execute("INSERT INTO legacy_sales (net_amount, payment_method_id, payment_method_name, sale_date, invoice_number, customer_name) VALUES (200.0, 1, 'Cash', '2025-05-19 10:00:00', 'LEG-1', 'Legacy Cust')");

        TransactionFilter filterAll = new TransactionFilter(null, null, null, null, null, null, null, null, 1, 10, "transaction_date", "ASC");
        PagedResult<Transaction> all = repository.findTransactions(filterAll);
        assertEquals(3, all.totalCount());

        TransactionFilter filterType = new TransactionFilter(null, null, List.of("payment"), null, null, null, null, null, 1, 10, "transaction_date", "ASC");
        PagedResult<Transaction> typed = repository.findTransactions(filterType);
        assertEquals(2, typed.totalCount());

        TransactionFilter filterDate = new TransactionFilter("2025-05-20", "2025-05-21", null, null, null, null, null, null, 1, 10, "transaction_date", "ASC");
        PagedResult<Transaction> withinDate = repository.findTransactions(filterDate);
        assertEquals(2, withinDate.totalCount());

        TransactionFilter filterAmount = new TransactionFilter(null, null, null, new BigDecimal("60"), new BigDecimal("300"), null, null, null, 1, 10, "transaction_date", "ASC");
        PagedResult<Transaction> amounts = repository.findTransactions(filterAmount);
        assertEquals(2, amounts.totalCount());

        TransactionFilter filterSearch = new TransactionFilter(null, null, null, null, null, null, null, "INV-100", 1, 10, "transaction_date", "ASC");
        PagedResult<Transaction> search = repository.findTransactions(filterSearch);
        assertEquals(2, search.totalCount()); // the two standard transactions belong to sale_id 1 which has INV-100
    }
}
