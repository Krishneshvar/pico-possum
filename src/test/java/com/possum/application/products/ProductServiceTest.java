package com.possum.application.products;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.variants.VariantService;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Product;
import com.possum.domain.model.Variant;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.ProductRepository;
import com.possum.domain.repositories.VariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private VariantService variantService;
    @Mock private VariantRepository variantRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private TransactionManager transactionManager;
    @Mock private AppPaths appPaths;
    @Mock private SettingsStore settingsStore;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, variantService, variantRepository, auditRepository, transactionManager, appPaths, settingsStore);
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
    @DisplayName("Should create product with variants successfully")
    void createProductWithVariants_success() {
        ProductService.VariantCommand v1 = new ProductService.VariantCommand(null, "64GB", "SKU1", new BigDecimal("100"), new BigDecimal("80"), 5, true, "active", 10, null);
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand("iPhone", "Apple phone", 1L, "active", null, List.of(v1), null, 1L);

        when(productRepository.insertProduct(any(Product.class))).thenReturn(100L);

        long productId = productService.createProductWithVariants(cmd);

        assertEquals(100L, productId);
        verify(productRepository).insertProduct(argThat(p -> p.name().equals("iPhone")));
        verify(variantService).addVariantWithoutTransaction(any());
        verify(auditRepository).insertAuditLog(any());
    }

    @Test
    @DisplayName("Should throw validation error if no variants provided")
    void createProductWithVariants_noVariants_fail() {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand("iPhone", null, 1L, "active", null, List.of(), null, 1L);
        assertThrows(ValidationException.class, () -> productService.createProductWithVariants(cmd));
    }

    @Test
    @DisplayName("Should throw NotFound during get if product doesn't exist")
    void getProductWithVariants_notFound_fail() {
        when(productRepository.findProductWithVariants(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> productService.getProductWithVariants(99L));
    }

    @Test
    @DisplayName("Should fetch product with variants successfully")
    void getProductWithVariants_success() {
        Product p = new Product(1L, "Widget", null, 1L, null, null, null, "active", null, 100, LocalDateTime.now(), null, null);
        Variant v = new Variant(10L, 1L, "Widget", "Default", "SKU1", new BigDecimal("10"), new BigDecimal("5"), 5, true, "active", null, 100, null, null, LocalDateTime.now(), null, null);
        
        ProductRepository.ProductWithVariants result = new ProductRepository.ProductWithVariants(p, List.of(v));
        when(productRepository.findProductWithVariants(1L)).thenReturn(Optional.of(result));
        when(productRepository.findProductTaxes(1L)).thenReturn(List.of());

        ProductService.ProductWithVariantsDTO dto = productService.getProductWithVariants(1L);
        
        assertEquals("Widget", dto.product().name());
        assertEquals(1, dto.variants().size());
    }

    @Test
    @DisplayName("Should process update with existing and new variants")
    void updateProduct_mixed_variants_success() {
        Product oldP = new Product(1L, "Old", null, 1L, null, null, null, "active", null, 0, LocalDateTime.now(), null, null);
        when(productRepository.findProductById(1L)).thenReturn(Optional.of(oldP));
        
        ProductService.VariantCommand existingV = new ProductService.VariantCommand(10L, "V1", "SKU1", new BigDecimal("10"), new BigDecimal("5"), 5, true, "active", 100, "adj");
        ProductService.VariantCommand newV = new ProductService.VariantCommand(null, "V2", "SKU2", new BigDecimal("20"), new BigDecimal("15"), 5, false, "active", 50, null);
        ProductService.UpdateProductCommand cmd = new ProductService.UpdateProductCommand("New", "Desc", 1L, "active", null, List.of(existingV, newV), null, 1L);

        productService.updateProduct(1L, cmd);

        verify(variantService).updateVariantWithoutTransaction(any());
        verify(variantService).addVariantWithoutTransaction(any());
        verify(productRepository).updateProductById(eq(1L), any());
    }
}
