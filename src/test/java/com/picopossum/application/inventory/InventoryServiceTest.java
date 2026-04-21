package com.picopossum.application.inventory;

import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.exceptions.InsufficientStockException;
import com.picopossum.domain.model.StockMovement;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.AuditRepository;
import com.picopossum.domain.repositories.InventoryRepository;
import com.picopossum.shared.dto.GeneralSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.picopossum.domain.model.ProductFlow;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        inventoryService = new InventoryService(
                inventoryRepository, productFlowService, auditRepository, 
                transactionManager, jsonService, settingsStore
        );
        
        lenient().when(transactionManager.runInTransaction(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("Should receive inventory and create stock movement")
    void receiveInventory_success() {
        when(inventoryRepository.insertStockMovement(any())).thenReturn(500L);
        when(inventoryRepository.getStockByProductId(1L)).thenReturn(20);

        long movementId = inventoryService.receiveInventory(1L, 20, "Manual restock");

        assertEquals(500L, movementId);
        verify(inventoryRepository).insertStockMovement(argThat(m -> 
            m.productId() == 1L && m.quantityChange() == 20 && "receive".equals(m.reason())
        ));
        verify(auditRepository).log(eq("stock_movements"), eq(500L), eq("CREATE"), any());
    }

    @Test
    @DisplayName("Should deduct stock and create negative movement")
    void deductStock_success() {
        when(inventoryRepository.getStockByProductId(1L)).thenReturn(100);

        inventoryService.deductStock(1L, 15, InventoryReason.SALE, "sale", 1000L);

        verify(inventoryRepository).insertStockMovement(argThat(m -> 
            m.productId() == 1L && m.quantityChange() == -15 && "sale".equals(m.reason())
        ));
    }

    @Test
    @DisplayName("Should block deduction if stock insufficient and restrictions enabled")
    void deductStock_insufficient_fail() throws Exception {
        GeneralSettings settings = mock(GeneralSettings.class);
        when(settings.isInventoryAlertsAndRestrictionsEnabled()).thenReturn(true);
        when(settingsStore.loadGeneralSettings()).thenReturn(settings);
        
        when(inventoryRepository.getStockByProductId(1L)).thenReturn(5);

        assertThrows(InsufficientStockException.class, () -> 
            inventoryService.deductStock(1L, 10, InventoryReason.SALE, "sale", 1001L));
    }

    @Test
    @DisplayName("Should allow deduction if stock insufficient but restrictions disabled")
    void deductStock_insufficient_success_when_allowed() throws Exception {
        GeneralSettings settings = mock(GeneralSettings.class);
        when(settings.isInventoryAlertsAndRestrictionsEnabled()).thenReturn(false);
        when(settingsStore.loadGeneralSettings()).thenReturn(settings);
        
        when(inventoryRepository.getStockByProductId(1L)).thenReturn(5);

        inventoryService.deductStock(1L, 10, InventoryReason.SALE, "sale", 1002L);

        verify(inventoryRepository).insertStockMovement(argThat(m -> 
            m.productId() == 1L && m.quantityChange() == -10
        ));
    }

    @Test
    @DisplayName("Should adjust inventory correctly")
    void adjustInventory_success() throws Exception {
        GeneralSettings settings = mock(GeneralSettings.class);
        when(settings.isInventoryAlertsAndRestrictionsEnabled()).thenReturn(false);
        when(settingsStore.loadGeneralSettings()).thenReturn(settings);
        
        when(inventoryRepository.insertStockMovement(any())).thenReturn(600L);
        
        inventoryService.adjustInventory(1L, -5, InventoryReason.DAMAGE, "audit", 10L, "Found broken");

        verify(inventoryRepository).insertStockMovement(argThat(m -> 
            m.productId() == 1L && m.quantityChange() == -5 && "damage".equals(m.reason())
        ));
        verify(auditRepository).log(eq("stock_movements"), eq(600L), eq("CREATE"), any());
    }

    @Test
    @DisplayName("Domain Validation: StockMovement blocks invalid creation")
    void domain_stockMovement_invariants() {
        assertThrows(IllegalArgumentException.class, () -> 
            new StockMovement(null, null, 10, "sale", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> 
            new StockMovement(null, 1L, 0, "sale", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> 
            new StockMovement(null, 1L, 10, "", null, null, null, null));
    }

    @Test
    @DisplayName("Domain Validation: ProductFlow blocks invalid creation")
    void domain_productFlow_invariants() {
        assertThrows(IllegalArgumentException.class, () -> 
            new ProductFlow(null, null, "sale", 1, null, null, "P1", "C1", 1L, "INV-1", "Cash", null));
        assertThrows(IllegalArgumentException.class, () -> 
            new ProductFlow(null, 1L, "", 1, null, null, "P1", "C1", 1L, "INV-1", "Cash", null));
        assertThrows(IllegalArgumentException.class, () -> 
            new ProductFlow(null, 1L, "sale", 0, null, null, "P1", "C1", 1L, "INV-1", "Cash", null));
        
        ProductFlow flow = new ProductFlow(null, 1L, "sale", 1, null, null, "P1", "C1", 1L, "INV-1234", "Cash", null);
        assertEquals("1234", flow.shortBillRefNumber());
    }
}
