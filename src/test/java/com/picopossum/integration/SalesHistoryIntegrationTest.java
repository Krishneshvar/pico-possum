package com.picopossum.integration;

import com.picopossum.domain.model.LegacySale;
import com.picopossum.domain.model.Sale;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.repositories.sqlite.SqliteSalesRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.SaleFilter;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SalesHistoryIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static SqliteSalesRepository salesRepository;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-history-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();

        salesRepository = new SqliteSalesRepository(databaseManager);
        
        seedData();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();

        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        if (appPaths != null) {
            try { deleteDirectory(appPaths.getAppRoot()); }
            catch (Exception e) { System.err.println("Cleanup failed: " + e.getMessage()); }
        }
    }

    private static void seedData() {
        // Seed Customers
        long c1 = insertCustomer("John Doe", "9876543210", "john@example.com");
        long c2 = insertCustomer("Jane Smith", "5551234567", null);
        long c3 = insertCustomer("Bob Brown", "1112223333", "bob@example.com");

        // 1. Normal Sale - John Doe
        Sale s1 = new Sale(null, "INV-1001", null, 
            new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, 
            "paid", "fulfilled", c1, "John Doe", "9876543210", "john@example.com", 
            "System Admin", 1L, "Cash", "INV-1001");
        long s1Id = salesRepository.insertSale(s1);
        spoofSaleDate(s1Id, LocalDateTime.now().minusDays(2));

        // 2. Normal Sale - Jane Smith
        Sale s2 = new Sale(null, "INV-1002", null, 
            new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 
            "partially_paid", "pending", c2, "Jane Smith", "5551234567", null, 
            "System Admin", 2L, "Card", "INV-1002");
        long s2Id = salesRepository.insertSale(s2);
        spoofSaleDate(s2Id, LocalDateTime.now().minusDays(1));

        // 3. Normal Sale - Cancelled
        Sale s3 = new Sale(null, "INV-1003", null, 
            new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 
            "cancelled", "cancelled", c3, "Bob Brown", "1112223333", "bob@example.com", 
            "System Admin", 1L, "Cash", "INV-1003");
        long s3Id = salesRepository.insertSale(s3);
        // Leave s3 as today

        // 4. Legacy Sale
        LegacySale ls1 = new LegacySale("LEG-2001", LocalDateTime.now().minusDays(10), 
            "C001", "Old Customer", new BigDecimal("1000.00"), 1L, "Cash", "import.csv");
        salesRepository.upsertLegacySale(ls1);
    }

    private static void spoofSaleDate(long saleId, LocalDateTime date) {
        try (java.sql.Connection conn = databaseManager.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement("UPDATE sales SET sale_date = ? WHERE id = ?")) {
            // SQLite expects "YYYY-MM-DD HH:MM:SS"
            String formattedDate = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(1, formattedDate);
            stmt.setLong(2, saleId);
            stmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to spoof date", e);
        }
    }

    private static long insertCustomer(String name, String phone, String email) {
        try (java.sql.Connection conn = databaseManager.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO customers (name, phone, email) VALUES (?, ?, ?)", 
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, phone);
            stmt.setString(3, email);
            stmt.executeUpdate();
            try (java.sql.ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to seed customer", e);
        }
        return -1;
    }

    @Test
    @Order(1)
    @DisplayName("Should fetch all unified sales with default filter")
    void shouldFetchAllUnifiedSales() {
        SaleFilter filter = new SaleFilter(null, null, null, null, null, null, null, 1, 15, "sale_date", "DESC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        assertEquals(4, result.totalCount());
        assertEquals(4, result.items().size());
        
        // Legacy sale should be present and marked appropriately
        boolean hasLegacy = result.items().stream().anyMatch(s -> "LEG-2001".equals(s.invoiceNumber()));
        assertTrue(hasLegacy, "Legacy sale should be included in unified results");
    }

    @Test
    @Order(2)
    @DisplayName("Should filter by status")
    void shouldFilterByStatus() {
        SaleFilter filter = new SaleFilter(List.of("paid", "legacy"), null, null, null, null, null, null, 1, 15, "sale_date", "DESC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        assertEquals(2, result.totalCount());
        assertTrue(result.items().stream().allMatch(s -> "paid".equals(s.status()) || "legacy".equals(s.status())));
    }

    @Test
    @Order(3)
    @DisplayName("Should filter by search term (Invoice, Name, Phone, Email)")
    void shouldFilterBySearchTerm() {
        // By Phone
        SaleFilter f1 = new SaleFilter(null, null, null, null, null, null, "987654", 1, 15, "sale_date", "DESC", null, null);
        assertEquals(1, salesRepository.findSales(f1).totalCount());

        // By Name
        SaleFilter f2 = new SaleFilter(null, null, null, null, null, null, "Jane", 1, 15, "sale_date", "DESC", null, null);
        assertEquals(1, salesRepository.findSales(f2).totalCount());

        // By Email
        SaleFilter f3 = new SaleFilter(null, null, null, null, null, null, "bob@example", 1, 15, "sale_date", "DESC", null, null);
        assertEquals(1, salesRepository.findSales(f3).totalCount());

        // By Invoice
        SaleFilter f4 = new SaleFilter(null, null, null, null, null, null, "LEG-2", 1, 15, "sale_date", "DESC", null, null);
        assertEquals(1, salesRepository.findSales(f4).totalCount());
    }

    @Test
    @Order(5)
    @DisplayName("Should filter by minimum amount")
    void shouldFilterByMinAmount() {
        SaleFilter filter = new SaleFilter(null, null, null, null,
                new BigDecimal("400.00"), null, null, 1, 15, "sale_date", "DESC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        // Only s1 (500) and legacy (1000) qualify
        assertEquals(2, result.totalCount());
        assertTrue(result.items().stream().allMatch(s -> s.totalAmount().compareTo(new BigDecimal("400.00")) >= 0));
    }

    @Test
    @Order(6)
    @DisplayName("Should filter by maximum amount")
    void shouldFilterByMaxAmount() {
        SaleFilter filter = new SaleFilter(null, null, null, null,
                null, new BigDecimal("200.00"), null, 1, 15, "sale_date", "DESC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        // s2 (150) and s3 (200) qualify
        assertEquals(2, result.totalCount());
    }

    @Test
    @Order(7)
    @DisplayName("Should filter by min AND max amount (range)")
    void shouldFilterByAmountRange() {
        SaleFilter filter = new SaleFilter(null, null, null, null,
                new BigDecimal("100.00"), new BigDecimal("300.00"), null,
                1, 15, "sale_date", "DESC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        // Only s2 (150) and s3 (200) fit in [100, 300]
        assertEquals(2, result.totalCount());
    }

    @Test
    @Order(8)
    @DisplayName("Should return empty result for a search term that matches nothing")
    void shouldReturnEmptyOnNoMatch() {
        SaleFilter filter = new SaleFilter(null, null, null, null, null, null,
                "XYZNONEXISTENT999", 1, 15, "sale_date", "DESC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        assertEquals(0, result.totalCount());
        assertTrue(result.items().isEmpty());
    }

    @Test
    @Order(9)
    @DisplayName("Pagination should respect page and size")
    void shouldPaginateCorrectly() {
        // With 4 records and page size 2, we should get 2 pages
        SaleFilter page1Filter = new SaleFilter(null, null, null, null, null, null,
                null, 1, 2, "sale_date", "DESC", null, null);
        SaleFilter page2Filter = new SaleFilter(null, null, null, null, null, null,
                null, 2, 2, "sale_date", "DESC", null, null);

        PagedResult<Sale> page1 = salesRepository.findSales(page1Filter);
        PagedResult<Sale> page2 = salesRepository.findSales(page2Filter);

        assertEquals(2, page1.items().size());
        assertEquals(2, page2.items().size());
        assertEquals(4, page1.totalCount());
        assertEquals(2, page1.totalPages());
    }

    @Test
    @Order(10)
    @DisplayName("Should sort by total_amount ascending")
    void shouldSortByAmountAscending() {
        SaleFilter filter = new SaleFilter(null, null, null, null, null, null,
                null, 1, 15, "total_amount", "ASC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        // Ensure results are in ascending order of totalAmount
        List<Sale> items = result.items();
        for (int i = 1; i < items.size(); i++) {
            assertTrue(items.get(i - 1).totalAmount().compareTo(items.get(i).totalAmount()) <= 0,
                    "Results should be sorted ascending by total_amount");
        }
    }

    @Test
    @Order(11)
    @DisplayName("Should reject invalid sort column and fall back to sale_date")
    void shouldFallBackToDefaultSortColumn() {
        // An invalid sort column should not crash — the service defensively defaults to sale_date
        SaleFilter filter = new SaleFilter(null, null, null, null, null, null,
                null, 1, 15, "invalid_column_xyz", "ASC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        // Should still return all records without error
        assertEquals(4, result.totalCount());
    }

    @Test
    @Order(12)
    @DisplayName("Legacy sale should have negative ID in unified view")
    void legacySale_shouldHaveNegativeId() {
        SaleFilter filter = new SaleFilter(List.of("legacy"), null, null, null, null, null,
                null, 1, 15, "sale_date", "DESC", null, null);
        PagedResult<Sale> result = salesRepository.findSales(filter);

        assertEquals(1, result.totalCount());
        // Legacy sale IDs are negated in the CTE to avoid collisions with real sale IDs
        assertTrue(result.items().get(0).id() < 0,
                "Legacy sales should have a negative ID to distinguish them from real sales");
    }

    @Test
    @Order(13)
    @DisplayName("SaleStats should correctly count paid, partial, and cancelled sales")
    void shouldReturnCorrectSaleStats() {
        SaleFilter filter = new SaleFilter(null, null, null, null, null, null,
                null, 1, 15, "sale_date", "DESC", null, null);
        var stats = salesRepository.getSaleStats(filter);

        assertEquals(4, stats.totalBills());
        assertEquals(2, stats.paidCount());   // s1 (paid) + ls1 (legacy treated as paid)
        assertEquals(1, stats.partialOrDraftCount()); // s2 (partially_paid)
        assertEquals(1, stats.cancelledOrRefundedCount()); // s3 (cancelled)
    }

    @Test
    @Order(14)
    @DisplayName("findSaleByInvoiceNumber should return sale by exact invoice")
    void shouldFindByExactInvoiceNumber() {
        Optional<Sale> result = salesRepository.findSaleByInvoiceNumber("INV-1001");
        assertTrue(result.isPresent());
        assertEquals("INV-1001", result.get().invoiceNumber());
    }

    @Test
    @Order(15)
    @DisplayName("findSaleByInvoiceNumber should return empty for non-existent invoice")
    void shouldReturnEmptyForMissingInvoice() {
        Optional<Sale> result = salesRepository.findSaleByInvoiceNumber("DOESNOTEXIST");
        assertTrue(result.isEmpty());
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) {
                    // Ignore on Windows
                }
            });
        }
    }
}
