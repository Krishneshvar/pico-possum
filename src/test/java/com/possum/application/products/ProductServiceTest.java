package com.possum.application.products;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Product;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.ProductRepository;
import com.possum.domain.repositories.InventoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private TransactionManager transactionManager;
    @Mock private AppPaths appPaths;
    @Mock private SettingsStore settingsStore;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, inventoryRepository, auditRepository, transactionManager, appPaths, settingsStore);
        AuthContext.setCurrentUser(new AuthUser(1L, "Admin", "admin", List.of(), List.of("products.manage")));
        
        // Mock transaction manager to run the supplier immediately
        lenient().when(transactionManager.runInTransaction(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should create product successfully")
    void createProduct_success() {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "iPhone", "Apple phone", 1L, 1L, "SKU1",
                new BigDecimal("100"), new BigDecimal("80"), 10, "active", null, 5, 1L
        );

        when(productRepository.insertProduct(any(Product.class))).thenReturn(100L);

        long productId = productService.createProduct(cmd);

        assertEquals(100L, productId);
        verify(productRepository).insertProduct(argThat(p -> p.name().equals("iPhone") && p.sku().equals("SKU1")));
        verify(inventoryRepository).insertInventoryLot(any());
        verify(inventoryRepository).insertInventoryAdjustment(any());
        verify(auditRepository).insertAuditLog(any());
    }

    @Test
    @DisplayName("Should throw validation error if name is missing")
    void createProduct_noName_fail() {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "", null, 1L, 1L, "SKU1",
                new BigDecimal("100"), new BigDecimal("80"), 10, "active", null, 0, 1L
        );
        assertThrows(ValidationException.class, () -> productService.createProduct(cmd));
    }

    @Test
    @DisplayName("Should throw NotFound during get if product doesn't exist")
    void getProductById_notFound_fail() {
        when(productRepository.findProductById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> productService.getProductById(99L));
    }

    @Test
    @DisplayName("Should fetch product successfully")
    void getProductById_success() {
        Product p = new Product(1L, "Widget", null, 1L, null, 1L, null, "SKU1", new BigDecimal("10"), new BigDecimal("5"), 10, "active", null, null, null, null, null);
        
        when(productRepository.findProductById(1L)).thenReturn(Optional.of(p));

        Product result = productService.getProductById(1L);
        
        assertEquals("Widget", result.name());
        assertEquals("SKU1", result.sku());
    }

    @Test
    @DisplayName("Should update product successfully")
    void updateProduct_success() {
        Product oldP = new Product(1L, "Old", null, 1L, null, 1L, null, "SKU1", new BigDecimal("10"), new BigDecimal("5"), 10, "active", null, null, null, null, null);
        when(productRepository.findProductById(1L)).thenReturn(Optional.of(oldP));
        
        ProductService.UpdateProductCommand cmd = new ProductService.UpdateProductCommand(
                "New", "Desc", 1L, 1L, "SKU2",
                new BigDecimal("20"), new BigDecimal("15"), 20, "active", null, 50, "correction", 1L
        );

        productService.updateProduct(1L, cmd);

        verify(productRepository).updateProductById(eq(1L), any());
        verify(inventoryRepository).insertInventoryAdjustment(any());
        verify(auditRepository).insertAuditLog(any());
    }
}
