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
        long adminRoleId = queryLong("SELECT id FROM roles WHERE name = 'admin'");
        String username = "test-user-" + UUID.randomUUID();

        User inserted = userRepository.insertUserWithRoles(
                new User(null, "Test User", username, "hash-123", true, null, null, null),
                List.of(adminRoleId)
        );

        assertNotNull(inserted.id());
        assertEquals(username, inserted.username());
        assertTrue(userRepository.findUserByUsername(username).isPresent());
        assertFalse(userRepository.getUserPermissions(inserted.id()).isEmpty());
    }

    @Test
    void shouldInsertUpdateAndQueryCategory() {
        String baseName = "Category-" + UUID.randomUUID();
        Category category = categoryRepository.insertCategory(baseName, null);

        assertNotNull(category.id());
        assertEquals(baseName, category.name());

        int changes = categoryRepository.updateCategoryById(category.id(), baseName + "-Updated", false, null);
        assertTrue(changes >= 1);
        Category updated = categoryRepository.findCategoryById(category.id()).orElseThrow();
        assertEquals(baseName + "-Updated", updated.name());
    }

    @Test
    void shouldReturnZeroForCategoryNoOpUpdate() {
        Category category = categoryRepository.insertCategory("NoOp-" + UUID.randomUUID(), null);
        assertEquals(0, categoryRepository.updateCategoryById(category.id(), null, false, null));
    }

    @Test
    void shouldAllowSettingCategoryParentToNull() {
        Category parent = categoryRepository.insertCategory("Parent-" + UUID.randomUUID(), null);
        Category child = categoryRepository.insertCategory("Child-" + UUID.randomUUID(), parent.id());
        assertTrue(categoryRepository.updateCategoryById(child.id(), null, true, null) >= 1);
        assertNull(categoryRepository.findCategoryById(child.id()).orElseThrow().parentId());
    }

    @Test
    void shouldSupportCustomerRepositoryBehavior() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String phone = "555-" + suffix.substring(0, 4);
        Customer inserted = customerRepository.insertCustomer("Customer " + suffix, phone, suffix + "@mail.com", "Addr", null, false).orElseThrow();
        assertNotNull(inserted.id());

        Customer updated = customerRepository.updateCustomerById(inserted.id(), "Updated " + suffix, null, null, null, null, false).orElseThrow();
        assertTrue(updated.name().startsWith("Updated"));

        assertFalse(customerRepository.findCustomers(new com.possum.shared.dto.CustomerFilter(
                phone, 1, 10, 1, 10, "name", "ASC"
        )).items().isEmpty());

        assertTrue(customerRepository.softDeleteCustomer(inserted.id()));
    }

    @Test
    void shouldReturnZeroForProductNoOpUpdateAndHandleStockStatusFilter() {
        long productId = productRepository.insertProduct(new Product(
                null,
                "Prod-" + UUID.randomUUID(),
                "desc",
                null,
                null,
                null,
                null,
                "active",
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(0, productRepository.updateProductById(productId, new Product(
                null, null, null, null, null, null, null, null, null, null, null, null, null
        )));

        var filtered = productRepository.findProducts(new ProductFilter(
                null,
                null,
                null,
                null,
                1,
                25,
                "name",
                "ASC"
        ));
        assertNotNull(filtered.items());
    }

    @Test
    void shouldInsertAndQuerySaleData() {
        long userId = ensureAnyUser();
        long variantId = ensureAnyVariant();

        String invoice = "INV-" + UUID.randomUUID().toString().substring(0, 8);
        long saleId = salesRepository.insertSale(
                new Sale(
                        null,
                        invoice,
                        null,
                        new BigDecimal("120.00"),
                        new BigDecimal("120.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("5.00"),
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
                        variantId,
                        null,
                        null,
                        null,
                        2,
                        new BigDecimal("60.00"),
                        new BigDecimal("40.00"),
                        new BigDecimal("2.50"),
                        new BigDecimal("5.00"),
                        new BigDecimal("2.50"),
                        new BigDecimal("5.00"),
                        null,
                        BigDecimal.ZERO,
                        0
                )
        );

        salesRepository.insertTransaction(
                new Transaction(
                        null,
                        new BigDecimal("120.00"),
                        "payment",
                        1L,
                        null,
                        "completed",
                        null,
                        invoice,
                        null,
                        null
                ),
                saleId
        );

        assertTrue(salesRepository.findSaleById(saleId).isPresent());
        assertFalse(salesRepository.findSaleItems(saleId).isEmpty());
        assertFalse(salesRepository.findTransactionsBySaleId(saleId).isEmpty());
    }

    @Test
    void shouldRollbackTransactionOnFailure() {
        String rollbackName = "Rollback-" + UUID.randomUUID();

        assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(() -> {
                    categoryRepository.insertCategory(rollbackName, null);
                    throw new RuntimeException("force rollback");
                })
        );

        int count = queryInt("SELECT COUNT(*) FROM categories WHERE name = ?", rollbackName);
        assertEquals(0, count);
    }

    @Test
    void shouldUseSavepointForNestedTransactions() {
        String outerName = "Outer-" + UUID.randomUUID();
        String innerName = "Inner-" + UUID.randomUUID();

        transactionManager.runInTransaction(() -> {
            categoryRepository.insertCategory(outerName, null);
            assertThrows(RuntimeException.class, () ->
                    transactionManager.runInTransaction(() -> {
                        categoryRepository.insertCategory(innerName, null);
                        throw new RuntimeException("rollback inner");
                    })
            );
            return null;
        });

        assertEquals(1, queryInt("SELECT COUNT(*) FROM categories WHERE name = ?", outerName));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM categories WHERE name = ?", innerName));
    }

    private static long ensureAnyUser() {
        try {
            long userId = queryLong("SELECT id FROM users ORDER BY id LIMIT 1");
            if (userId > 0) {
                return userId;
            }
        } catch (IllegalStateException ignored) {
            // fall through to insert
        }

        long roleId = queryLong("SELECT id FROM roles WHERE name = 'admin'");
        User user = userRepository.insertUserWithRoles(
                new User(null, "Seed User", "seed-" + UUID.randomUUID(), "seed-hash", true, null, null, null),
                List.of(roleId)
        );
        return user.id();
    }

    private static long ensureAnyVariant() {
        try {
            long variantId = queryLong("SELECT id FROM variants ORDER BY id LIMIT 1");
            if (variantId > 0) return variantId;
        } catch (IllegalStateException ignored) {}

        long categoryId = categoryRepository.insertCategory("Test Cat", null).id();
        long productId = productRepository.insertProduct(new Product(null, "Test Prod", "desc", categoryId, null, null, null, "active", null, null, null, null, null));
        
        try (PreparedStatement statement = prepare("INSERT INTO variants (product_id, name, sku, mrp, cost_price, stock_alert_cap, is_default, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", productId, "Default", "SKU123", new BigDecimal("10.00"), new BigDecimal("5.00"), 10, 1, "active")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to insert variant", ex);
        }
        throw new IllegalStateException("Failed to insert variant");
    }

    private static long queryLong(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("No result for query: " + sql);
            }
            return rs.getLong(1);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed queryLong for SQL: " + sql, ex);
        }
    }

    private static int queryInt(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("No result for query: " + sql);
            }
            return rs.getInt(1);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed queryInt for SQL: " + sql, ex);
        }
    }

    private static PreparedStatement prepare(String sql, Object... params) throws SQLException {
        Connection connection = databaseManager.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
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
