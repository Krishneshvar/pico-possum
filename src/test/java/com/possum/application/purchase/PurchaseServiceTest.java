package com.possum.application.purchase;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.domain.enums.PurchaseStatus;
import com.possum.domain.exceptions.AuthorizationException;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.PurchaseOrder;
import com.possum.domain.model.PurchaseOrderItem;
import com.possum.domain.model.Supplier;
import com.possum.domain.model.Variant;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.PurchaseOrderFilter;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private VariantRepository variantRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductFlowRepository productFlowRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private TransactionManager transactionManager;
    @Mock private ConnectionProvider connectionProvider;
    @Mock private JsonService jsonService;

    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() throws Exception {
        purchaseService = new PurchaseService(purchaseRepository, supplierRepository, variantRepository, inventoryRepository, productFlowRepository, auditRepository, transactionManager, connectionProvider, jsonService);
        AuthContext.setCurrentUser(new AuthUser(1L, "Admin", "admin", List.of(), List.of("purchase.manage")));
        
        lenient().when(transactionManager.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.PreparedStatement insertStmt = mock(java.sql.PreparedStatement.class);
        java.sql.PreparedStatement selectStmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        
        lenient().when(connectionProvider.getConnection()).thenReturn(conn);
        lenient().when(conn.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            return sql.contains("INSERT") ? insertStmt : selectStmt;
        });
        lenient().when(selectStmt.executeQuery()).thenReturn(rs);
        lenient().when(rs.next()).thenReturn(true);
        lenient().when(rs.getLong("last_sequence")).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch all purchase orders")
    void getAllPurchaseOrders_success() {
        PurchaseOrderFilter filter = new PurchaseOrderFilter(1, 10, null, null, null, null, "created_at", "desc", null, null, null);
        PagedResult<PurchaseOrder> page = new PagedResult<>(List.of(), 0, 0, 1, 10);
        when(purchaseRepository.getAllPurchaseOrders(filter)).thenReturn(page);

        PagedResult<PurchaseOrder> result = purchaseService.getAllPurchaseOrders(filter);
        assertEquals(0, result.totalCount());
    }

    @Test
    @DisplayName("Should fetch by id successfully")
    void getPurchaseOrderById_success() {
        PurchaseOrder po = new PurchaseOrder(1L, "PXX123", 1L, "sup", 1L, null, "pending", LocalDateTime.now(), null, 1L, "jane", 0, BigDecimal.ZERO);
        when(purchaseRepository.getPurchaseOrderById(1L)).thenReturn(Optional.of(po));
        when(purchaseRepository.getPurchaseOrderItems(1L)).thenReturn(List.of());

        PurchaseService.PurchaseOrderDetail result = purchaseService.getPurchaseOrderById(1L);
        assertNotNull(result);
        assertEquals(1L, result.purchaseOrder().id());
    }

    @Test
    @DisplayName("Should throw NotFound if purchase order not found")
    void getPurchaseOrderById_notFound() {
        when(purchaseRepository.getPurchaseOrderById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> purchaseService.getPurchaseOrderById(1L));
    }

    @Test
    @DisplayName("Should create purchase order successfully")
    void createPurchaseOrder_success() {
        List<PurchaseService.PurchaseOrderItemRequest> items = List.of(
            new PurchaseService.PurchaseOrderItemRequest(10L, 50, new BigDecimal("10.00"))
        );

        when(supplierRepository.findSupplierById(1L)).thenReturn(Optional.of(mock(Supplier.class)));
        when(variantRepository.findVariantByIdSync(10L)).thenReturn(Optional.of(mock(Variant.class)));
        when(purchaseRepository.createPurchaseOrder(eq(1L), anyString(), eq(1L), eq(1L), anyList())).thenReturn(100L);
        PurchaseOrder po = new PurchaseOrder(100L, "PXX123", 1L, "sup", 1L, null, "pending", LocalDateTime.now(), null, 1L, "jane", 1, BigDecimal.TEN);
        when(purchaseRepository.getPurchaseOrderById(100L)).thenReturn(Optional.of(po));

        PurchaseService.PurchaseOrderDetail result = purchaseService.createPurchaseOrder(1L, 1L, 1L, items);
        assertNotNull(result);
        assertEquals(100L, result.purchaseOrder().id());
    }

    @Test
    @DisplayName("Should block unauthorized creation")
    void createPurchaseOrder_unauthorized() {
        AuthContext.setCurrentUser(new AuthUser(1L, "User", "user", List.of(), List.of()));
        assertThrows(AuthorizationException.class, () -> purchaseService.createPurchaseOrder(1L, 1L, 1L, List.of()));
    }

    @Test
    @DisplayName("Should validate items during creation")
    void createPurchaseOrder_invalidQuantity_fail() {
        List<PurchaseService.PurchaseOrderItemRequest> items = List.of(
            new PurchaseService.PurchaseOrderItemRequest(10L, 0, new BigDecimal("10.00"))
        );
        when(supplierRepository.findSupplierById(1L)).thenReturn(Optional.of(mock(Supplier.class)));
        assertThrows(ValidationException.class, () -> purchaseService.createPurchaseOrder(1L, 1L, 1L, items));
    }
}
