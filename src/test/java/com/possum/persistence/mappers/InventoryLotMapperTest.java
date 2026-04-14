package com.possum.persistence.mappers;

import com.possum.domain.model.InventoryLot;
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
class InventoryLotMapperTest {

    @Mock private ResultSet resultSet;
    private final InventoryLotMapper mapper = new InventoryLotMapper();

    @Test
    @DisplayName("Should map ResultSet to InventoryLot object properly")
    void mapInventoryLot_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getLong("variant_id")).thenReturn(10L);
        lenient().when(resultSet.getString("batch_number")).thenReturn("B-123");
        lenient().when(resultSet.getString("manufactured_date")).thenReturn("2023-01-01 00:00:00");
        lenient().when(resultSet.getString("expiry_date")).thenReturn("2024-01-01 00:00:00");
        lenient().when(resultSet.getInt("quantity")).thenReturn(100);
        lenient().when(resultSet.getObject("unit_cost")).thenReturn(new BigDecimal("50.25"));
        lenient().when(resultSet.getLong("purchase_order_item_id")).thenReturn(50L);
        lenient().when(resultSet.wasNull()).thenReturn(false);
        lenient().when(resultSet.getString("created_at")).thenReturn("2023-01-01 10:00:00");

        InventoryLot lot = mapper.map(resultSet);

        assertNotNull(lot);
        assertEquals(1L, lot.id());
        assertEquals(10L, lot.variantId());
        assertEquals("B-123", lot.batchNumber());
        assertEquals(LocalDateTime.of(2023, 1, 1, 0, 0, 0), lot.manufacturedDate());
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), lot.expiryDate());
        assertEquals(100, lot.quantity());
        assertEquals(new BigDecimal("50.25"), lot.unitCost());
        assertEquals(50L, lot.purchaseOrderItemId());
    }

    @Test
    @DisplayName("Should handle nulls in InventoryLotMapper")
    void mapInventoryLot_nulls_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getLong("variant_id")).thenReturn(10L);
        lenient().when(resultSet.getLong("purchase_order_item_id")).thenReturn(0L);
        lenient().when(resultSet.getString(anyString())).thenReturn(null);
        lenient().when(resultSet.wasNull()).thenReturn(true);

        InventoryLot lot = mapper.map(resultSet);

        assertNotNull(lot);
        assertNull(lot.purchaseOrderItemId());
    }
}
