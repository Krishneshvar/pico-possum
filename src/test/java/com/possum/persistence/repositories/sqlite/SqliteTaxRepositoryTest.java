package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;
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

class SqliteTaxRepositoryTest {

    private Connection connection;
    private SqliteTaxRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteTaxRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE tax_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                country_code TEXT,
                region_code TEXT,
                pricing_mode TEXT,
                is_active INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE tax_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                description TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE tax_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tax_profile_id INTEGER,
                tax_category_id INTEGER,
                rule_scope TEXT,
                min_price REAL,
                max_price REAL,
                min_invoice_total REAL,
                max_invoice_total REAL,
                customer_type TEXT,
                rate_percent REAL,
                is_compound INTEGER,
                priority INTEGER,
                valid_from TEXT,
                valid_to TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tax_category_id INTEGER
            )
        """);
    }

    @Test
    void createTaxProfile_insertsSuccessfully() {
        TaxProfile profile = new TaxProfile(null, "Standard", "US", "CA", "tax_exclusive", true, null, null);
        long id = repository.createTaxProfile(profile);
        assertTrue(id > 0);
    }

    @Test
    void getActiveTaxProfile_returnsActiveProfile() {
        repository.createTaxProfile(new TaxProfile(null, "Inactive", "US", "CA", "tax_exclusive", false, null, null));
        repository.createTaxProfile(new TaxProfile(null, "Active", "US", "CA", "tax_inclusive", true, null, null));

        Optional<TaxProfile> active = repository.getActiveTaxProfile();
        assertTrue(active.isPresent());
        assertEquals("Active", active.get().name());
    }

    @Test
    void getAllTaxProfiles_returnsAllProfiles() {
        repository.createTaxProfile(new TaxProfile(null, "One", "US", "CA", "tax_exclusive", false, null, null));
        repository.createTaxProfile(new TaxProfile(null, "Two", "US", "CA", "tax_inclusive", true, null, null));

        List<TaxProfile> all = repository.getAllTaxProfiles();
        assertEquals(2, all.size());
    }

    @Test
    void updateTaxProfile_updatesSuccessfully() {
        long id = repository.createTaxProfile(new TaxProfile(null, "One", "US", "CA", "tax_exclusive", false, null, null));
        
        TaxProfile updates = new TaxProfile(null, "Updated", null, null, null, true, null, null);
        int rows = repository.updateTaxProfile(id, updates);
        assertEquals(1, rows);

        Optional<TaxProfile> active = repository.getActiveTaxProfile();
        assertTrue(active.isPresent());
        assertEquals("Updated", active.get().name());
    }

    @Test
    void deleteTaxProfile_deletesSuccessfully() {
        long id = repository.createTaxProfile(new TaxProfile(null, "One", "US", "CA", "tax_exclusive", false, null, null));
        
        int rows = repository.deleteTaxProfile(id);
        assertEquals(1, rows);
        
        List<TaxProfile> all = repository.getAllTaxProfiles();
        assertTrue(all.isEmpty());
    }

    @Test
    void createTaxCategory_insertsSuccessfully() {
        long id = repository.createTaxCategory("Electronics", "Tech");
        assertTrue(id > 0);
    }

    @Test
    void getAllTaxCategories_returnsCategoriesWithCount() throws SQLException {
        long catId = repository.createTaxCategory("Electronics", "Tech");
        connection.createStatement().execute("INSERT INTO products (tax_category_id) VALUES (" + catId + ")");

        List<TaxCategory> listed = repository.getAllTaxCategories();
        assertEquals(1, listed.size());
        assertEquals("Electronics", listed.get(0).name());
        assertEquals(1, listed.get(0).productCount());
    }

    @Test
    void updateTaxCategory_updatesSuccessfully() {
        long catId = repository.createTaxCategory("Electronics", "Tech");
        int rows = repository.updateTaxCategory(catId, "Tech", "Gadgets");
        assertEquals(1, rows);

        List<TaxCategory> listed = repository.getAllTaxCategories();
        assertEquals("Tech", listed.get(0).name());
    }

    @Test
    void deleteTaxCategory_failsIfUsed() throws SQLException {
        long catId = repository.createTaxCategory("Electronics", "Tech");
        connection.createStatement().execute("INSERT INTO products (tax_category_id) VALUES (" + catId + ")");

        assertThrows(IllegalStateException.class, () -> repository.deleteTaxCategory(catId));
    }

    @Test
    void deleteTaxCategory_deletesIfUnused() {
        long catId = repository.createTaxCategory("Electronics", "Tech");
        int rows = repository.deleteTaxCategory(catId);
        assertEquals(1, rows);
    }

    @Test
    void createTaxRule_insertsSuccessfully() {
        TaxRule rule = new TaxRule(null, 1L, 1L, "item", new BigDecimal("10.0"), null, null, null, "retail",
                                   new BigDecimal("5.0"), false, 1, null, null, null, null, null);
        long id = repository.createTaxRule(rule);
        assertTrue(id > 0);
    }

    @Test
    void getTaxRulesByProfileId_returnsRules() {
        TaxRule rule = new TaxRule(null, 1L, 1L, "item", new BigDecimal("10.0"), null, null, null, "retail",
                                   new BigDecimal("5.0"), false, 1, null, null, null, null, null);
        repository.createTaxRule(rule);

        List<TaxRule> rules = repository.getTaxRulesByProfileId(1L);
        assertEquals(1, rules.size());
        assertEquals(0, new BigDecimal("5.0").compareTo(rules.get(0).ratePercent()));
    }

    @Test
    void updateTaxRule_updatesSuccessfully() {
        TaxRule rule = new TaxRule(null, 1L, 1L, "item", null, null, null, null, "retail",
                                   new BigDecimal("5.0"), false, 1, null, null, null, null, null);
        long id = repository.createTaxRule(rule);

        TaxRule updates = new TaxRule(null, null, null, null, null, null, null, null, null, new BigDecimal("8.0"), null, null, null, null, null, null, null);
        int rows = repository.updateTaxRule(id, updates);
        assertEquals(1, rows);

        List<TaxRule> rules = repository.getTaxRulesByProfileId(1L);
        assertEquals(0, new BigDecimal("8.0").compareTo(rules.get(0).ratePercent()));
    }

    @Test
    void deleteTaxRule_deletesSuccessfully() {
        TaxRule rule = new TaxRule(null, 1L, 1L, "item", null, null, null, null, "retail",
                                   new BigDecimal("5.0"), false, 1, null, null, null, null, null);
        long id = repository.createTaxRule(rule);

        int rows = repository.deleteTaxRule(id);
        assertEquals(1, rows);

        List<TaxRule> rules = repository.getTaxRulesByProfileId(1L);
        assertTrue(rules.isEmpty());
    }
}
