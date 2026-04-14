package com.possum.ui.dashboard;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.inventory.InventoryService;
import com.possum.application.reports.ReportsService;
import com.possum.application.reports.dto.SalesReportSummary;
import com.possum.application.reports.dto.TopProduct;
import com.possum.domain.model.Variant;
import com.possum.ui.JavaFXInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private ReportsService reportsService;
    @Mock private InventoryService inventoryService;
    @Mock private javafx.scene.control.Label dailySalesLabel;
    @Mock private javafx.scene.control.Label transactionsLabel;
    @Mock private javafx.scene.control.Label lowStockLabel;
    @Mock private com.possum.ui.common.controls.DataTableView<TopProduct> topProductsTable;
    @Mock private com.possum.ui.common.controls.DataTableView<com.possum.domain.model.Variant> lowStockTable;
    @Mock private javafx.scene.control.TableView<TopProduct> topTableView;
    @Mock private javafx.scene.control.TableView<com.possum.domain.model.Variant> lowTableView;
    @Mock private com.possum.infrastructure.backup.DatabaseBackupService backupService;
    @Mock private javafx.scene.control.Label backupStatusLabel;

    private DashboardController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("dashboard:view")));
        
        lenient().when(reportsService.getSalesSummary(any(), any(), any())).thenReturn(
            new SalesReportSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        );
        lenient().when(reportsService.getTopProducts(any(), any(), anyInt(), any())).thenReturn(List.of());
        lenient().when(inventoryService.getLowStockAlerts()).thenReturn(List.of());
        lenient().when(backupService.findLatestBackup()).thenReturn(java.util.Optional.empty());

        controller = new DashboardController(reportsService, inventoryService, backupService);
        
        lenient().when(topProductsTable.getTableView()).thenReturn(topTableView);
        lenient().when(lowStockTable.getTableView()).thenReturn(lowTableView);
        lenient().when(topTableView.getColumns()).thenReturn(javafx.collections.FXCollections.observableArrayList());
        lenient().when(lowTableView.getColumns()).thenReturn(javafx.collections.FXCollections.observableArrayList());

        setField(controller, "dailySalesLabel", dailySalesLabel);
        setField(controller, "transactionsLabel", transactionsLabel);
        setField(controller, "lowStockLabel", lowStockLabel);
        setField(controller, "backupStatusLabel", backupStatusLabel);
        setField(controller, "topProductsTable", topProductsTable);
        setField(controller, "lowStockTable", lowStockTable);
        
        controller.initialize();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = DashboardController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should load dashboard data correctly")
    void loadDashboardData_success() {
        LocalDate today = LocalDate.now();
        SalesReportSummary summary = new SalesReportSummary(
            10, new BigDecimal("1000.00"), new BigDecimal("180.00"), new BigDecimal("100.00"),
            new BigDecimal("1080.00"), BigDecimal.ZERO, new BigDecimal("600.00"), new BigDecimal("400.00"),
            new BigDecimal("1000.00"), new BigDecimal("100.00")
        );
        List<TopProduct> topProducts = List.of(
            new TopProduct(1L, "Product A", "Standard", "SKU001", 5, new BigDecimal("500.00"))
        );
        List<Variant> lowStockVariants = List.of(
            createTestVariant(1L, "Low Stock Product", 2)
        );

        when(reportsService.getSalesSummary(eq(today), eq(today), isNull())).thenReturn(summary);
        when(reportsService.getTopProducts(eq(today), eq(today), anyInt(), isNull())).thenReturn(topProducts);
        when(inventoryService.getLowStockAlerts()).thenReturn(lowStockVariants);

        controller.refresh();

        verify(reportsService, atLeastOnce()).getSalesSummary(eq(today), eq(today), isNull());
        verify(reportsService, atLeastOnce()).getTopProducts(eq(today), eq(today), anyInt(), isNull());
        verify(inventoryService, atLeastOnce()).getLowStockAlerts();
        
        verify(dailySalesLabel, atLeastOnce()).setText(anyString());
        verify(transactionsLabel, atLeastOnce()).setText("10");
        verify(lowStockLabel, atLeastOnce()).setText("1");
    }

    private com.possum.domain.model.Variant createTestVariant(Long id, String name, int stock) {
        return new com.possum.domain.model.Variant(
            id, 1L, "Test Product", name, "SKU" + id, 
            java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(5), 5, true, 
            "ACTIVE", null, stock, "Electronics", null, 
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), null
        );
    }
}
