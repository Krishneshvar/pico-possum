package com.picopossum.ui.dashboard;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.reports.ReportsService;
import com.picopossum.application.reports.dto.SalesReportSummary;
import com.picopossum.application.reports.dto.TopProduct;
import com.picopossum.domain.model.Product;
import com.picopossum.infrastructure.monitoring.PerformanceMonitor;
import com.picopossum.ui.JavaFXInitializer;
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
    @Mock private com.picopossum.ui.common.controls.DataTableView<TopProduct> topProductsTable;
    @Mock private com.picopossum.ui.common.controls.DataTableView<com.picopossum.domain.model.Product> lowStockTable;
    @Mock private javafx.scene.control.TableView<TopProduct> topTableView;
    @Mock private javafx.scene.control.TableView<com.picopossum.domain.model.Product> lowTableView;
    @Mock private com.picopossum.infrastructure.backup.DatabaseBackupService backupService;
    @Mock private PerformanceMonitor performanceMonitor;
    @Mock private javafx.scene.control.Label backupStatusLabel;

    private DashboardController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser"));
        
        lenient().when(reportsService.getHourlyAnalytics(any(), any())).thenReturn(List.of());
        lenient().when(reportsService.getSalesSummary(any(), any(), any())).thenReturn(
            new SalesReportSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        );
        lenient().when(reportsService.getTopProducts(any(), any(), anyInt(), any())).thenReturn(List.of());
        lenient().when(inventoryService.getLowStockAlerts()).thenReturn(List.of());
        lenient().when(backupService.findLatestBackup()).thenReturn(java.util.Optional.empty());

        controller = new DashboardController(reportsService, inventoryService, backupService, performanceMonitor);
        
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
        setField(controller, "salesTrendChart", new javafx.scene.chart.LineChart<>(new javafx.scene.chart.CategoryAxis(), new javafx.scene.chart.NumberAxis()));
        setField(controller, "stockStatCard", new javafx.scene.layout.VBox());
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
            10, new BigDecimal("1000.00"), new BigDecimal("100.00"),
            new BigDecimal("1080.00"), BigDecimal.ZERO, new BigDecimal("600.00"), new BigDecimal("400.00"),
            new BigDecimal("1000.00"), new BigDecimal("100.00")
        );
        List<TopProduct> topProducts = List.of(
            new TopProduct(1L, "Product A", "SKU001", 5, new BigDecimal("500.00"))
        );
        List<Product> lowStockProducts = List.of(
            createTestProduct(1L, "Low Stock Product", 2)
        );

        when(reportsService.getSalesSummary(eq(today), eq(today), isNull())).thenReturn(summary);
        when(reportsService.getTopProducts(eq(today), eq(today), anyInt(), isNull())).thenReturn(topProducts);
        when(inventoryService.getLowStockAlerts()).thenReturn(lowStockProducts);

        controller.refresh();
        
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        verify(reportsService, atLeastOnce()).getSalesSummary(eq(today), eq(today), isNull());
        verify(reportsService, atLeastOnce()).getTopProducts(eq(today), eq(today), anyInt(), isNull());
        verify(inventoryService, atLeastOnce()).getLowStockAlerts();
    }

    private com.picopossum.domain.model.Product createTestProduct(Long id, String name, int stock) {
        return new com.picopossum.domain.model.Product(
            id, name, "desc", 1L, null, java.math.BigDecimal.ZERO, "SKU" + id,
            null, new java.math.BigDecimal("10.00"), new java.math.BigDecimal("12.00"), 5, "active", null, stock, null, null, null
        );
    }
}

