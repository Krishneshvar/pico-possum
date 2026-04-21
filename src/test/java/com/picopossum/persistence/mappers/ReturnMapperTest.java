package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.Return;
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
        when(resultSet.getLong(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            return switch (col) {
                case "id" -> 1L;
                case "sale_id" -> 100L;
                case "payment_method_id" -> 2L;
                default -> 0L;
            };
        });
        
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            return switch (col) {
                case "reason" -> "Defective item";
                case "invoice_number" -> "INV-123";
                case "processed_by_name" -> "System Admin";
                case "payment_method_name" -> "Cash";
                case "invoice_id" -> "ID-001";
                case "created_at" -> "2023-10-15 14:30:00";
                default -> null;
            };
        });
        
        when(resultSet.getObject("total_refund")).thenReturn(new BigDecimal("99.99"));
        when(resultSet.wasNull()).thenReturn(false);

        Return ret = mapper.map(resultSet);

        assertNotNull(ret);
        assertEquals(1L, ret.id());
        assertEquals(100L, ret.saleId());
        assertEquals("Defective item", ret.reason());
        assertEquals("INV-123", ret.invoiceNumber());
        assertEquals("System Admin", ret.processedByName());
        assertEquals(new BigDecimal("99.99"), ret.totalRefund());
        assertEquals(2L, ret.paymentMethodId());
        assertEquals("Cash", ret.paymentMethodName());
        assertEquals("ID-001", ret.invoiceId());
        assertEquals(LocalDateTime.of(2023, 10, 15, 14, 30), ret.createdAt());
    }

    @Test
    @DisplayName("Should handle null IDs and strings in ReturnMapper")
    void mapReturn_nulls_success() throws SQLException {
        // Use lenient or just provide all stubs
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getLong("sale_id")).thenReturn(100L); // Valid sale_id
        
        when(resultSet.getString(anyString())).thenReturn(null);
        when(resultSet.getObject("total_refund")).thenReturn(BigDecimal.ZERO); // Valid totalRefund
        when(resultSet.wasNull()).thenReturn(true); // Still simulate wasNull for optional fields

        Return ret = mapper.map(resultSet);

        assertNotNull(ret);
        assertEquals(1L, ret.id());
        assertEquals(100L, ret.saleId());
        assertEquals(BigDecimal.ZERO, ret.totalRefund());
        assertNull(ret.paymentMethodId());
        assertNull(ret.paymentMethodName());
    }
}
