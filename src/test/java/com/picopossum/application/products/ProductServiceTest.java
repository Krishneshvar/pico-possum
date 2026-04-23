package com.picopossum.application.products;

import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.Product;
import com.picopossum.infrastructure.filesystem.FileStorageService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.application.audit.AuditService;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.domain.repositories.InventoryRepository;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.ui.sales.ProductSearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private AuditService auditService;
    @Mock private TransactionManager transactionManager;
    @Mock private SettingsStore settingsStore;
    @Mock private FileStorageService storageService;
    @Mock private com.picopossum.infrastructure.serialization.JsonService jsonService;
    @Mock private ProductSearchIndex searchIndex;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        ProductValidator validator = new ProductValidator();
        productService = new ProductService(productRepository, inventoryRepository, auditService, 
                transactionManager, settingsStore, validator, storageService, jsonService, searchIndex);
        
        lenient().when(transactionManager.runInTransaction(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("Should create product successfully without user dependency")
    void createProduct_success() {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "iPhone", "Apple phone", 1L, "SKU1",
                new BigDecimal("100"), new BigDecimal("80"), 10, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 5,
                BigDecimal.ZERO, "BAR1"
        );

        when(productRepository.existsBySku("SKU1")).thenReturn(false);
        when(productRepository.insertProduct(any(Product.class))).thenReturn(100L);

        long productId = productService.createProduct(cmd);

        assertEquals(100L, productId);
        verify(productRepository).insertProduct(argThat(p -> p.name().equals("iPhone") && p.sku().equals("SKU1")));
        verify(inventoryRepository).insertStockMovement(argThat(sm -> sm.quantityChange() == 5));
    }

    @Test
    @DisplayName("Should create product successfully with empty barcode (sanitized to null)")
    void createProduct_emptyBarcode_sanitized() {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "iPhone", "Apple phone", 1L, "SKU1",
                new BigDecimal("100"), new BigDecimal("80"), 10, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 0,
                BigDecimal.ZERO, ""
        );

        when(productRepository.existsBySku("SKU1")).thenReturn(false);
        when(productRepository.insertProduct(any(Product.class))).thenReturn(101L);

        productService.createProduct(cmd);

        verify(productRepository).insertProduct(argThat(p -> p.barcode() == null));
    }

    @Test
    @DisplayName("Should block duplicate SKU during creation")
    void createProduct_duplicateSku_fail() {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "iPhone", "Apple phone", 1L, "DUPE",
                new BigDecimal("100"), new BigDecimal("80"), 10, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 0,
                BigDecimal.ZERO, "BAR2"
        );

        when(productRepository.existsBySku("DUPE")).thenReturn(true);

        assertThrows(ValidationException.class, () -> productService.createProduct(cmd));
    }

    @Test
    @DisplayName("Should fetch product successfully")
    void getProductById_success() {
        Product p = new Product(1L, "Widget", "Desc", 1L, null, BigDecimal.ZERO, "SKU1", null, 
                new BigDecimal("10"), new BigDecimal("5"), 0, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 10, null, null, null);
        
        when(productRepository.findProductById(1L)).thenReturn(Optional.of(p));

        Product result = productService.getProductById(1L);
        
        assertEquals("Widget", result.name());
        verify(productRepository).findProductById(1L);
    }

    @Test
    @DisplayName("Should delete product and perform final stock cleanup")
    void deleteProduct_success() {
        Product p = new Product(1L, "Delete Me", "desc", 1L, null, BigDecimal.ZERO, "OLD", null, 
                new BigDecimal("10"), new BigDecimal("5"), 0, com.picopossum.domain.model.ProductStatus.ACTIVE, "/path/image.jpg", 10, null, null, null);
        
        when(productRepository.findProductById(1L)).thenReturn(Optional.of(p));
        when(inventoryRepository.getStockByProductId(1L)).thenReturn(10);

        productService.deleteProduct(1L);

        verify(productRepository).softDeleteProduct(1L);
        verify(storageService).delete("/path/image.jpg");
        verify(inventoryRepository).insertStockMovement(argThat(sm -> sm.quantityChange() == -10));
    }

    @Test
    @DisplayName("Should update product successfully with empty barcode (sanitized to null)")
    void updateProduct_emptyBarcode_sanitized() {
        Product oldP = new Product(1L, "Widget", "Desc", 1L, null, BigDecimal.ZERO, "SKU1", "BAR1", 
                new BigDecimal("10"), new BigDecimal("5"), 0, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 10, null, null, null);
        
        when(productRepository.findProductById(1L)).thenReturn(Optional.of(oldP));

        ProductService.UpdateProductCommand cmd = new ProductService.UpdateProductCommand(
                "Widget Updated", "Desc", 1L, "SKU1",
                new BigDecimal("12"), new BigDecimal("6"), 0, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 10, "manual",
                BigDecimal.ZERO, ""
        );

        productService.updateProduct(1L, cmd);

        verify(productRepository).updateProductById(eq(1L), argThat(p -> p.barcode() == null));
    }

    @Test
    @DisplayName("Should throw NotFoundException for non-existent product")
    void getProductById_notFound() {
        when(productRepository.findProductById(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> productService.getProductById(999L));
    }
}
