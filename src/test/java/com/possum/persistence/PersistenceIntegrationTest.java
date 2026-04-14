package com.possum.persistence;

import com.possum.domain.model.Category;
import com.possum.domain.model.Customer;
import com.possum.domain.model.Product;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.domain.model.Transaction;
import com.possum.domain.model.User;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.persistence.db.DatabaseManager;
import com.possum.persistence.db.TransactionManager;
import com.possum.persistence.repositories.sqlite.SqliteCategoryRepository;
import com.possum.persistence.repositories.sqlite.SqliteCustomerRepository;
import com.possum.persistence.repositories.sqlite.SqliteProductRepository;
import com.possum.persistence.repositories.sqlite.SqliteSalesRepository;
import com.possum.persistence.repositories.sqlite.SqliteUserRepository;
import com.possum.shared.dto.ProductFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;
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
        String appDir = "possum-test-" + UUID.randomUUID();
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
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (appPaths != null) {
            deleteDirectory(appPaths.getAppRoot());
        }
    }

    @Test
    void shouldInsertAndQueryUser() {
        long adminRoleId = queryLong("SELECT id FROM roles WHERE name = 'admin' LIMIT 1");
        String username = "test-user-" + UUID.randomUUID();

        User inserted = userRepository.insertUserWithRoles(
                new User(null, "Test User", username, "hash-123", true, null, null, null),
                List.of(adminRoleId)
        );

        assertNotNull(inserted.id());
        assertEquals(username, inserted.username());
        assertTrue(userRepository.findUserByUsername(username).isPresent());
    }

    @Test
    void shouldInsertAndQuerySaleData() {
        long userId = ensureAnyUser();
        long productId = ensureAnyProduct();

        String invoice = "INV-" + UUID.randomUUID().toString().substring(0, 8);
        long saleId = salesRepository.insertSale(
                new Sale(
                        null,
                        invoice,
                        null,
                        new BigDecimal("120.00"),
                        new BigDecimal("120.00"),
                        BigDecimal.ZERO,
                        "paid",
                        "fulfilled",
                        null,
                        userId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        salesRepository.insertSaleItem(
                new SaleItem(
                        null,
                        saleId,
                        productId,
                        "SKU123",
                        "Test Prod",
                        2,
                        new BigDecimal("60.00"),
                        new BigDecimal("40.00"),
                        BigDecimal.ZERO,
                        0
                )
        );

        assertTrue(salesRepository.findSaleById(saleId).isPresent());
        assertFalse(salesRepository.findSaleItems(saleId).isEmpty());
    }

    private static long ensureAnyUser() {
        try {
            long userId = queryLong("SELECT id FROM users ORDER BY id LIMIT 1");
            return userId;
        } catch (Exception ignored) {}

        long roleId = queryLong("SELECT id FROM roles WHERE name = 'admin' LIMIT 1");
        User user = userRepository.insertUserWithRoles(
                new User(null, "Seed User", "seed-" + UUID.randomUUID(), "seed-hash", true, null, null, null),
                List.of(roleId)
        );
        return user.id();
    }

    private static long ensureAnyProduct() {
        try {
            long productId = queryLong("SELECT id FROM products ORDER BY id LIMIT 1");
            return productId;
        } catch (Exception ignored) {}

        long categoryId = categoryRepository.insertCategory("Test Cat", null).id();
        Product product = new Product(null, "Test Prod", "desc", categoryId, "Cat Name", "SKU123", new BigDecimal("60.00"), new BigDecimal("40.00"), 10, "active", null, 0, null, null, null);
        return productRepository.insertProduct(product);
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No result for query: " + sql);
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed queryLong for SQL: " + sql, ex);
        }
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to delete test path: " + path, ex);
                }
            });
        }
    }
}
