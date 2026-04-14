package com.possum.persistence.mappers;

import com.possum.domain.model.Return;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnMapperTest {

    @Mock private ResultSet resultSet;
    private final ReturnMapper mapper = new ReturnMapper();

    @Test
    @DisplayName("Should map ResultSet to Return object properly")
    void mapReturn_success() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getLong("sale_id")).thenReturn(100L);
        when(resultSet.getLong("user_id")).thenReturn(5L);
        when(resultSet.getString("reason")).thenReturn("Defective item");
        when(resultSet.getString("invoice_number")).thenReturn("INV-123");
        when(resultSet.getString("processed_by_name")).thenReturn("Admin");
        when(resultSet.getObject("total_refund")).thenReturn(new BigDecimal("99.99"));
        when(resultSet.getLong("payment_method_id")).thenReturn(2L);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getString("created_at")).thenReturn("2023-10-15 14:30:00");

        Return ret = mapper.map(resultSet);

        assertNotNull(ret);
        assertEquals(1L, ret.id());
        assertEquals(100L, ret.saleId());
        assertEquals(5L, ret.userId());
        assertEquals("Defective item", ret.reason());
        assertEquals("INV-123", ret.invoiceNumber());
        assertEquals("Admin", ret.processedByName());
        assertEquals(new BigDecimal("99.99"), ret.totalRefund());
        assertEquals(2L, ret.paymentMethodId());
        assertEquals(LocalDateTime.of(2023, 10, 15, 14, 30), ret.createdAt());
    }

    @Test
    @DisplayName("Should handle null payment method in ReturnMapper")
    void mapReturn_nullPM_success() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getLong("payment_method_id")).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(true);

        Return ret = mapper.map(resultSet);

        assertNotNull(ret);
        assertNull(ret.paymentMethodId());
    }
}
