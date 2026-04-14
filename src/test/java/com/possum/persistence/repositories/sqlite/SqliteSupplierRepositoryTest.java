package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.PaymentPolicy;
import com.possum.domain.model.Supplier;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SupplierFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSupplierRepositoryTest {

    private Connection connection;
    private SqliteSupplierRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteSupplierRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE payment_policies (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                days_to_pay INTEGER,
                description TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                contact_person TEXT,
                phone TEXT,
                email TEXT,
                address TEXT,
                gstin TEXT,
                payment_policy_id INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                FOREIGN KEY(payment_policy_id) REFERENCES payment_policies(id)
            )
        """);
    }

    @Test
    void createPaymentPolicy_insertsSuccessfully() {
        long id = repository.createPaymentPolicy("Net 30", 30, "Pay within 30 days");
        assertTrue(id > 0);
    }

    @Test
    void getPaymentPolicies_returnsList() {
        repository.createPaymentPolicy("Net 30", 30, "Desc 1");
        repository.createPaymentPolicy("Net 60", 60, "Desc 2");

        List<PaymentPolicy> policies = repository.getPaymentPolicies();
        assertEquals(2, policies.size());
    }

    @Test
    void createSupplier_insertsSuccessfully() {
        long policyId = repository.createPaymentPolicy("Net 30", 30, null);
        
        Supplier supplier = new Supplier(null, "Tech Corp", "John Doe", "1234567890", "john@tech.com", 
                                         "123 Street", "GST123", policyId, null, null, null, null);
        long id = repository.createSupplier(supplier);
        assertTrue(id > 0);
    }

    @Test
    void findSupplierById_found_returnsSupplier() {
        long policyId = repository.createPaymentPolicy("Net 30", 30, null);
        
        Supplier supplier = new Supplier(null, "Tech Corp", "John Doe", "1234567890", "john@tech.com", 
                                         "123 Street", "GST123", policyId, null, null, null, null);
        long id = repository.createSupplier(supplier);

        Optional<Supplier> found = repository.findSupplierById(id);
        assertTrue(found.isPresent());
        assertEquals("Tech Corp", found.get().name());
        assertEquals("Net 30", found.get().paymentPolicyName());
    }

    @Test
    void findSupplierById_notFound_returnsEmpty() {
        Optional<Supplier> found = repository.findSupplierById(999L);
        assertFalse(found.isPresent());
    }

    @Test
    void updateSupplier_updatesSuccessfully() {
        long policyId = repository.createPaymentPolicy("Net 30", 30, null);
        
        Supplier supplier = new Supplier(null, "Tech Corp", "John", "123", "a@a.c", "A", "G", policyId, null, null, null, null);
        long id = repository.createSupplier(supplier);

        Supplier updates = new Supplier(null, "New Corp", "Jane", "456", "b@b.c", "B", "H", policyId, null, null, null, null);
        int rows = repository.updateSupplier(id, updates);
        assertEquals(1, rows);

        Optional<Supplier> updated = repository.findSupplierById(id);
        assertEquals("New Corp", updated.get().name());
        assertEquals("Jane", updated.get().contactPerson());
    }

    @Test
    void deleteSupplier_softDeletesSuccessfully() {
        long policyId = repository.createPaymentPolicy("Net 30", 30, null);
        Supplier supplier = new Supplier(null, "Tech Corp", "John", "123", "a@a.c", "A", "G", policyId, null, null, null, null);
        long id = repository.createSupplier(supplier);

        int rows = repository.deleteSupplier(id);
        assertEquals(1, rows);

        Optional<Supplier> found = repository.findSupplierById(id);
        assertFalse(found.isPresent()); // findSupplierById filters deleted_at IS NULL
    }

    @Test
    void getAllSuppliers_withFilters_returnsCorrectlyFilteredResults() {
        long policy1 = repository.createPaymentPolicy("Net 30", 30, null);
        long policy2 = repository.createPaymentPolicy("Net 60", 60, null);

        repository.createSupplier(new Supplier(null, "Target A", "John", "123", "a@a.c", "A", "G", policy1, null, null, null, null));
        repository.createSupplier(new Supplier(null, "Target B", "Jane", "456", "b@b.c", "B", "H", policy2, null, null, null, null));
        repository.createSupplier(new Supplier(null, "Unrelated", "Jack", "789", "c@c.c", "C", "I", policy1, null, null, null, null));

        SupplierFilter all = new SupplierFilter(1, 10, null, null, "name", "ASC");
        assertEquals(3, repository.getAllSuppliers(all).totalCount());

        SupplierFilter search = new SupplierFilter(1, 10, "Target", null, "name", "ASC");
        assertEquals(2, repository.getAllSuppliers(search).totalCount());

        SupplierFilter policySearch = new SupplierFilter(1, 10, null, List.of(policy2), "name", "ASC");
        assertEquals(1, repository.getAllSuppliers(policySearch).totalCount());
        assertEquals("Target B", repository.getAllSuppliers(policySearch).items().get(0).name());
    }
}
