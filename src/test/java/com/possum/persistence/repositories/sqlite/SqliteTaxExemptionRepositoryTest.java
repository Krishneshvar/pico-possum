package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.TaxExemption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteTaxExemptionRepositoryTest {

    private Connection connection;
    private SqliteTaxExemptionRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteTaxExemptionRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE tax_exemptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                exemption_type TEXT,
                certificate_number TEXT,
                reason TEXT,
                valid_from TEXT,
                valid_to TEXT,
                approved_by INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    @Test
    void save_insertsSuccessfully() {
        TaxExemption exemption = new TaxExemption(null, 1L, "Non-Profit", "CERT-123", "Charity", 
                                                  LocalDateTime.of(2025, 1, 1, 0, 0), 
                                                  LocalDateTime.of(2026, 1, 1, 0, 0), 
                                                  2L, null, null);
        TaxExemption saved = repository.save(exemption);
        
        assertNotNull(saved.id());
        assertEquals("Non-Profit", saved.exemptionType());
        assertEquals("CERT-123", saved.certificateNumber());
    }

    @Test
    void save_updatesSuccessfully() {
        TaxExemption exemption = new TaxExemption(null, 1L, "Non-Profit", "CERT-123", "Charity", 
                                                  LocalDateTime.of(2025, 1, 1, 0, 0), 
                                                  LocalDateTime.of(2026, 1, 1, 0, 0), 
                                                  2L, null, null);
        TaxExemption inserted = repository.save(exemption);

        TaxExemption updates = new TaxExemption(inserted.id(), 1L, "Government", "CERT-999", "Dept", 
                                                inserted.validFrom(), inserted.validTo(), 2L, null, null);
        TaxExemption updated = repository.save(updates);

        assertEquals("Government", updated.exemptionType());
        assertEquals("CERT-999", updated.certificateNumber());
    }

    @Test
    void findById_found_returnsExemption() {
        TaxExemption exemption = new TaxExemption(null, 1L, "Non-Profit", "CERT-123", "Charity", 
                                                  LocalDateTime.of(2025, 1, 1, 0, 0), 
                                                  LocalDateTime.of(2026, 1, 1, 0, 0), 
                                                  2L, null, null);
        TaxExemption saved = repository.save(exemption);

        Optional<TaxExemption> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
    }

    @Test
    void findById_notFound_returnsEmpty() {
        Optional<TaxExemption> found = repository.findById(999L);
        assertFalse(found.isPresent());
    }

    @Test
    void findByCustomerId_returnsExemptions() {
        repository.save(new TaxExemption(null, 10L, "T1", "C1", "R1", null, null, 1L, null, null));
        repository.save(new TaxExemption(null, 10L, "T2", "C2", "R2", null, null, 1L, null, null));
        repository.save(new TaxExemption(null, 20L, "T3", "C3", "R3", null, null, 1L, null, null));

        List<TaxExemption> list = repository.findByCustomerId(10L);
        assertEquals(2, list.size());
    }

    @Test
    void findActiveExemption_returnsActiveOnly() {
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 0, 0);
        
        // Expired
        repository.save(new TaxExemption(null, 1L, "T1", "C1", "R1", 
            LocalDateTime.of(2024, 1, 1, 0, 0), 
            LocalDateTime.of(2024, 12, 31, 0, 0), 1L, null, null));
            
        // Active
        repository.save(new TaxExemption(null, 1L, "T1", "C1", "R1", 
            LocalDateTime.of(2025, 1, 1, 0, 0), 
            LocalDateTime.of(2026, 12, 31, 0, 0), 1L, null, null));
            
        Optional<TaxExemption> active = repository.findActiveExemption(1L, now);
        assertTrue(active.isPresent());
        assertEquals(2026, active.get().validTo().getYear());
    }

    @Test
    void delete_deletesSuccessfully() {
        TaxExemption saved = repository.save(new TaxExemption(null, 1L, "T1", "C1", "R1", null, null, 1L, null, null));
        
        repository.delete(saved.id());
        
        Optional<TaxExemption> found = repository.findById(saved.id());
        assertFalse(found.isPresent());
    }
}
