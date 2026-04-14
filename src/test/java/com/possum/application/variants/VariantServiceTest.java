package com.possum.application.variants;

import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Variant;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.domain.repositories.VariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class VariantServiceTest {

    @Mock private VariantRepository variantRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private TransactionManager transactionManager;

    private VariantService variantService;

    @BeforeEach
    void setUp() {
        variantService = new VariantService(variantRepository, inventoryRepository, auditRepository, transactionManager);
        
        lenient().when(transactionManager.runInTransaction(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("Should add variant successfully")
    void addVariant_success() {
        VariantService.AddVariantCommand cmd = new VariantService.AddVariantCommand(
            1L, "Standard", "SKU1", new BigDecimal("10"), new BigDecimal("8"), 5, true, "active", 10, 1L
        );
        when(variantRepository.insertVariant(eq(1L), any(Variant.class))).thenReturn(100L);

        long variantId = variantService.addVariant(cmd);

        assertEquals(100L, variantId);
        verify(variantRepository).insertVariant(eq(1L), any());
        verify(inventoryRepository).insertInventoryLot(any());
        verify(inventoryRepository).insertInventoryAdjustment(any());
    }

    @Test
    @DisplayName("Should validate variant creation parameters")
    void addVariant_validation_fail() {
        VariantService.AddVariantCommand cmd = new VariantService.AddVariantCommand(
            1L, null, "SKU1", new BigDecimal("-1"), new BigDecimal("8"), 5, true, "active", 10, 1L
        );
        assertThrows(ValidationException.class, () -> variantService.addVariant(cmd));
    }

    @Test
    @DisplayName("Should update variant and trigger stock adjustment if changed")
    void updateVariant_with_stock_adjustment() {
        Variant existing = new Variant(10L, 1L, null, "Old", "SKU1", new BigDecimal("10"), new BigDecimal("8"), 5, true, "active", null, 50, null, null, LocalDateTime.now(), null, null);
        when(variantRepository.findVariantByIdSync(10L)).thenReturn(Optional.of(existing));
        when(inventoryRepository.getStockByVariantId(10L)).thenReturn(50);

        VariantService.UpdateVariantCommand cmd = new VariantService.UpdateVariantCommand(
            10L, "New", "SKU1", new BigDecimal("12"), new BigDecimal("9"), 5, true, "active", 60, "restock", 1L
        );

        variantService.updateVariant(cmd);

        verify(variantRepository).updateVariantById(any());
        verify(inventoryRepository).insertInventoryAdjustment(argThat(adj -> adj.quantityChange() == 10));
    }

    @Test
    @DisplayName("Should validate variant ownership correctly")
    void validateVariantOwnership_logic() {
        Variant v = new Variant(10L, 1L, null, "V", "S", new BigDecimal("1"), new BigDecimal("1"), 1, true, "active", null, 1, null, null, LocalDateTime.now(), null, null);
        when(variantRepository.findVariantByIdSync(10L)).thenReturn(Optional.of(v));

        assertDoesNotThrow(() -> variantService.validateVariantOwnership(10L, 1L));
        assertThrows(ValidationException.class, () -> variantService.validateVariantOwnership(10L, 2L));
    }
}
