package com.possum.persistence.mappers;

import com.possum.domain.model.PurchaseOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderMapperTest {

    @Mock private ResultSet resultSet;
    private final PurchaseOrderMapper mapper = new PurchaseOrderMapper();

    @Test
    @DisplayName("Should map ResultSet to PurchaseOrder properly")
    void mapPurchaseOrder_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("invoice_number")).thenReturn("PO-001");
        lenient().when(resultSet.getLong("supplier_id")).thenReturn(10L);
        lenient().when(resultSet.getString("supplier_name")).thenReturn("Big Supplier");
        lenient().when(resultSet.getLong("payment_method_id")).thenReturn(2L);
        lenient().when(resultSet.getString("status")).thenReturn("received");
        lenient().when(resultSet.getString("order_date")).thenReturn("2023-10-15 10:00:00");
        lenient().when(resultSet.getString("received_date")).thenReturn("2023-10-20 14:00:00");
        lenient().when(resultSet.getLong("created_by")).thenReturn(1L);
        lenient().when(resultSet.getInt("item_count")).thenReturn(5);
        lenient().when(resultSet.getObject("total_cost")).thenReturn(new BigDecimal("5000.00"));
        lenient().when(resultSet.wasNull()).thenReturn(false);

        PurchaseOrder po = mapper.map(resultSet);

        assertNotNull(po);
        assertEquals(1L, po.id());
        assertEquals("PO-001", po.invoiceNumber());
        assertEquals(10L, po.supplierId());
        assertEquals("Big Supplier", po.supplierName());
        assertEquals("received", po.status());
        assertEquals(LocalDateTime.of(2023, 10, 15, 10, 0), po.orderDate());
        assertEquals(LocalDateTime.of(2023, 10, 20, 14, 0), po.receivedDate());
        assertEquals(5, po.itemCount());
        assertEquals(new BigDecimal("5000.00"), po.totalCost());
    }

    @Test
    @DisplayName("Should handle missing optional columns in PurchaseOrder")
    void mapPurchaseOrder_nulls_success() throws SQLException {
        lenient().when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            if (col.equals("supplier_name")) throw new SQLException("Col missing");
            return null;
        });
        lenient().when(resultSet.wasNull()).thenReturn(true);
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getLong("supplier_id")).thenReturn(10L);
        lenient().when(resultSet.getLong("payment_method_id")).thenReturn(0L);
        lenient().when(resultSet.getLong("created_by")).thenReturn(0L);

        PurchaseOrder po = mapper.map(resultSet);

        assertNotNull(po);
        assertNull(po.supplierName());
        assertNull(po.itemCount());
    }
}
