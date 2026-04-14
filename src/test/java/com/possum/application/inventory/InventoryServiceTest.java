package com.possum.application.inventory;

import com.possum.domain.enums.InventoryReason;
import com.possum.domain.exceptions.InsufficientStockException;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.shared.dto.AvailableLot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductFlowService productFlowService;
    @Mock private AuditRepository auditRepository;
    @Mock private TransactionManager transactionManager;
    @Mock private JsonService jsonService;
    @Mock private SettingsStore settingsStore;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository, transactionManager, jsonService, settingsStore, new com.possum.domain.services.StockManager());
        
        lenient().when(transactionManager.runInTransaction(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("Should receive inventory and create lot/adjustment")
    void receiveInventory_success() {
        when(inventoryRepository.insertInventoryLot(any())).thenReturn(500L);
        when(inventoryRepository.getStockByVariantId(1L)).thenReturn(20);

        InventoryService.ReceiveInventoryResult result = inventoryService.receiveInventory(1L, 20, new BigDecimal("15.00"), "BATCH1", null, null, null, 1L);

        assertNotNull(result);
        assertEquals(500L, result.lotId());
        assertEquals(20, result.newStock());
        verify(inventoryRepository).insertInventoryLot(any());
        verify(inventoryRepository).insertInventoryAdjustment(any());
        verify(productFlowService).logProductFlow(eq(1L), any(), eq(20), any(), any());
    }

    @Test
    @DisplayName("Should deduct stock using FIFO across multiple lots")
    void deductStock_fifo_success() {
        when(inventoryRepository.getStockByVariantId(1L)).thenReturn(100);
        // Lot 1: 10 units, Lot 2: 50 units
        when(inventoryRepository.findAvailableLots(1L)).thenReturn(List.of(
            new AvailableLot(100L, 1L, null, null, null, 10, BigDecimal.TEN, null, java.time.LocalDateTime.now(), 10),
            new AvailableLot(101L, 1L, null, null, null, 50, BigDecimal.TEN, null, java.time.LocalDateTime.now(), 50)
        ));

        // Deduct 15 units
        inventoryService.deductStock(1L, 15, 1L, InventoryReason.SALE, "sale", 1000L);

        // Should create 2 adjustments: 10 from Lot 1, 5 from Lot 2
        verify(inventoryRepository, times(2)).insertInventoryAdjustment(any());
        verify(inventoryRepository).insertInventoryAdjustment(argThat(adj -> adj.lotId() == 100L && adj.quantityChange() == -10));
        verify(inventoryRepository).insertInventoryAdjustment(argThat(adj -> adj.lotId() == 101L && adj.quantityChange() == -5));
    }

    @Test
    @DisplayName("Should block deduction if stock insufficient and restrictions enabled")
    void deductStock_insufficient_fail() throws Exception {
        com.possum.shared.dto.GeneralSettings settings = mock(com.possum.shared.dto.GeneralSettings.class);
        when(settings.isInventoryAlertsAndRestrictionsEnabled()).thenReturn(true);
        when(settingsStore.loadGeneralSettings()).thenReturn(settings);
        
        when(inventoryRepository.getStockByVariantId(1L)).thenReturn(5);

        assertThrows(InsufficientStockException.class, () -> inventoryService.deductStock(1L, 10, 1L, InventoryReason.SALE, null, null));
    }

    @Test
    @DisplayName("Should allow deduction if stock insufficient but restrictions disabled")
    void deductStock_insufficient_success_when_allowed() throws Exception {
        com.possum.shared.dto.GeneralSettings settings = mock(com.possum.shared.dto.GeneralSettings.class);
        when(settings.isInventoryAlertsAndRestrictionsEnabled()).thenReturn(false);
        when(settingsStore.loadGeneralSettings()).thenReturn(settings);
        
        when(inventoryRepository.getStockByVariantId(1L)).thenReturn(5);
        when(inventoryRepository.findAvailableLots(1L)).thenReturn(List.of());

        InventoryService.DeductStockResult result = inventoryService.deductStock(1L, 10, 1L, InventoryReason.SALE, null, null);

        assertTrue(result.success());
        assertEquals(10, result.deducted());
        // Should create a headless adjustment (no lotId)
        verify(inventoryRepository).insertInventoryAdjustment(argThat(adj -> adj.lotId() == null && adj.quantityChange() == -10));
    }
}
