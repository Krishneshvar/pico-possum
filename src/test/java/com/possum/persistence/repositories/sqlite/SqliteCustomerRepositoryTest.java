package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Customer;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.shared.dto.CustomerFilter;
import com.possum.shared.dto.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteCustomerRepositoryTest {

    private Connection connection;
    private SqliteCustomerRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteCustomerRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                address TEXT,
                customer_type TEXT,
                is_tax_exempt INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
    }

    @Test
    void insert_validCustomer_insertsSuccessfully() {
        Optional<Customer> result = repository.insertCustomer(
                "John Doe", "1234567890", "john@example.com", "123 Main St", null, false);

        assertTrue(result.isPresent());
        assertEquals("John Doe", result.get().name());
        assertEquals("john@example.com", result.get().email());
        assertNotNull(result.get().id());
    }

    @Test
    void insert_duplicateEmail_insertsSuccessfully() {
        repository.insertCustomer("Customer 1", "1111111111", "same@example.com", "Address 1", null, false);
        Optional<Customer> result = repository.insertCustomer(
                "Customer 2", "2222222222", "same@example.com", "Address 2", null, false);

        assertTrue(result.isPresent());
        assertEquals("Customer 2", result.get().name());
    }

    @Test
    void findById_found_returnsCustomer() {
        Optional<Customer> inserted = repository.insertCustomer(
                "Jane Doe", "9876543210", "jane@example.com", "456 Oak St", null, false);

        Optional<Customer> result = repository.findCustomerById(inserted.get().id());

        assertTrue(result.isPresent());
        assertEquals("Jane Doe", result.get().name());
        assertEquals("jane@example.com", result.get().email());
    }

    @Test
    void findById_notFound_returnsEmpty() {
        Optional<Customer> result = repository.findCustomerById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findAll_withPagination_returnsPagedResult() {
        repository.insertCustomer("Customer 1", "1111111111", "c1@example.com", "Address 1", null, false);
        repository.insertCustomer("Customer 2", "2222222222", "c2@example.com", "Address 2", null, false);
        repository.insertCustomer("Customer 3", "3333333333", "c3@example.com", "Address 3", null, false);

        CustomerFilter filter = new CustomerFilter(null, null, null, 1, 2, "name", "ASC");
        PagedResult<Customer> result = repository.findCustomers(filter);

        assertEquals(3, result.totalCount());
        assertEquals(2, result.items().size());
        assertEquals(1, result.page());
        assertEquals(2, result.totalPages());
    }

    @Test
    void update_validChanges_updatesSuccessfully() {
        Optional<Customer> inserted = repository.insertCustomer(
                "Original Name", "1234567890", "original@example.com", "Original Address", null, false);

        Optional<Customer> updated = repository.updateCustomerById(
                inserted.get().id(), "Updated Name", "9999999999", "updated@example.com", "Updated Address", null, false);

        assertTrue(updated.isPresent());
        assertEquals("Updated Name", updated.get().name());
        assertEquals("updated@example.com", updated.get().email());
    }

    @Test
    void delete_softDelete_marksAsDeleted() {
        Optional<Customer> inserted = repository.insertCustomer(
                "To Delete", "1234567890", "delete@example.com", "Address", null, false);

        boolean deleted = repository.softDeleteCustomer(inserted.get().id());

        assertTrue(deleted);
        Optional<Customer> found = repository.findCustomerById(inserted.get().id());
        assertFalse(found.isPresent());
    }

    @Test
    void findByEmail_found_returnsCustomer() {
        repository.insertCustomer("John Doe", "1234567890", "john@example.com", "123 Main St", null, false);

        CustomerFilter filter = new CustomerFilter("john@example.com", null, null, 1, 10, "name", "ASC");
        PagedResult<Customer> result = repository.findCustomers(filter);

        assertEquals(1, result.totalCount());
        assertEquals("john@example.com", result.items().get(0).email());
    }

    @Test
    void findByPhone_found_returnsCustomer() {
        repository.insertCustomer("Jane Doe", "5555555555", "jane@example.com", "456 Oak St", null, false);

        CustomerFilter filter = new CustomerFilter("5555555555", null, null, 1, 10, "name", "ASC");
        PagedResult<Customer> result = repository.findCustomers(filter);

        assertEquals(1, result.totalCount());
        assertEquals("5555555555", result.items().get(0).phone());
    }

    @Test
    void search_byNameEmailPhone_returnsMatches() {
        repository.insertCustomer("Alice Smith", "1111111111", "alice@example.com", "Address 1", null, false);
        repository.insertCustomer("Bob Jones", "2222222222", "bob@example.com", "Address 2", null, false);

        CustomerFilter filter = new CustomerFilter("Alice", null, null, 1, 10, "name", "ASC");
        PagedResult<Customer> result = repository.findCustomers(filter);

        assertEquals(1, result.totalCount());
        assertEquals("Alice Smith", result.items().get(0).name());
    }

    @Test
    void count_totalCustomers_returnsCount() {
        repository.insertCustomer("Customer 1", "1111111111", "c1@example.com", "Address 1", null, false);
        repository.insertCustomer("Customer 2", "2222222222", "c2@example.com", "Address 2", null, false);
        repository.insertCustomer("Customer 3", "3333333333", "c3@example.com", "Address 3", null, false);

        CustomerFilter filter = new CustomerFilter(null, null, null, 1, 100, "name", "ASC");
        PagedResult<Customer> result = repository.findCustomers(filter);

        assertEquals(3, result.totalCount());
    }

    @Test
    void exists_byEmail_checksExistence() {
        repository.insertCustomer("Existing Customer", "1234567890", "exists@example.com", "Address", null, false);

        CustomerFilter filter = new CustomerFilter("exists@example.com", null, null, 1, 10, "name", "ASC");
        PagedResult<Customer> result = repository.findCustomers(filter);

        assertTrue(result.totalCount() > 0);
    }

    @Test
    void insert_withCustomerType_insertsSuccessfully() {
        Optional<Customer> result = repository.insertCustomer(
                "Wholesale Customer", "1234567890", "wholesale@example.com", "123 Main St", "Wholesaler", false);

        assertTrue(result.isPresent());
        assertEquals("Wholesaler", result.get().customerType());
    }
}
