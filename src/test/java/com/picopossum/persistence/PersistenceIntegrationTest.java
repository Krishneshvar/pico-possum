package com.picopossum.persistence;

import com.picopossum.domain.model.Customer;
import com.picopossum.domain.model.Product;
import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.domain.model.User;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.persistence.repositories.sqlite.SqliteCategoryRepository;
import com.picopossum.persistence.repositories.sqlite.SqliteCustomerRepository;
import com.picopossum.persistence.repositories.sqlite.SqliteProductRepository;
import com.picopossum.persistence.repositories.sqlite.SqliteSalesRepository;
import com.picopossum.persistence.repositories.sqlite.SqliteUserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SqliteUserRepository userRepository;
    private static SqliteCategoryRepository categoryRepository;
    private static SqliteCustomerRepository customerRepository;
    private static SqliteProductRepository productRepository;
    private static SqliteSalesRepository salesRepository;

    @BeforeAll
    static void beforeAll() {
        String appDir = "possum-test-persist-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        transactionManager = new TransactionManager(databaseManager);

        userRepository = new SqliteUserRepository(databaseManager);
        categoryRepository = new SqliteCategoryRepository(databaseManager);
        customerRepository = new SqliteCustomerRepository(databaseManager);
        productRepository = new SqliteProductRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @DisplayName("Should insert and query User record")
    void shouldInsertAndQueryUser() {
        String username = "test-user-" + UUID.randomUUID();
        User inserted = userRepository.insertUser(
                new User(null, "Test User", username, "hash-123", true, null, null, null)
        );

        assertNotNull(inserted.id());
        assertEquals(username, inserted.username());
        assertTrue(userRepository.findUserByUsername(username).isPresent());
    }

    @Test
    @DisplayName("Should flow a complete sale through the persistence layer")
    void shouldInsertAndQuerySaleData() {
        long productId = ensureAnyProduct();
        String invoice = "INV-" + UUID.randomUUID().toString().substring(0, 8);
        
        long saleId = salesRepository.insertSale(
                new Sale(
                        null, invoice, java.time.LocalDateTime.now(),
                        new BigDecimal("120.00"), new BigDecimal("120.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled",
                        null, "Guest", null, null, "System", 1L, "Cash", invoice
                )
        );

        salesRepository.insertSaleItem(
                new SaleItem(
                        null, saleId, productId, "SKU123", "Test Prod",
                        2, new BigDecimal("60.00"), new BigDecimal("40.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
                )
        );

        assertTrue(salesRepository.findSaleById(saleId).isPresent());
        assertFalse(salesRepository.findSaleItems(saleId).isEmpty());
    }

    private static long ensureAnyProduct() {
        try {
            return queryLong("SELECT id FROM products ORDER BY id LIMIT 1");
        } catch (Exception ignored) {}

        long categoryId = categoryRepository.insertCategory("Test Cat", null).id();
        Product product = new Product(null, "Test Prod", null, categoryId, "Cat Name", BigDecimal.ZERO, "SKU123", 
                null, new BigDecimal("60.00"), new BigDecimal("40.00"), 0, com.picopossum.domain.model.ProductStatus.ACTIVE, 
                null, 10, null, null, null);
        return productRepository.insertProduct(product);
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No result");
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Query failed", ex);
        }
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) {
                    throw new IllegalStateException("Delete failed: " + path, ex);
                }
            });
        }
    }
}
