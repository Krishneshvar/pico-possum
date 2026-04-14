package com.possum.persistence.mappers;

import com.possum.domain.model.Sale;
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
class SaleMapperTest {

    @Mock private ResultSet resultSet;
    private final SaleMapper mapper = new SaleMapper();

    @Test
    @DisplayName("Should map ResultSet to Sale object properly")
    void mapSale_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("invoice_number")).thenReturn("INV-001");
        lenient().when(resultSet.getString("sale_date")).thenReturn("2023-10-15 14:30:00");
        lenient().when(resultSet.getObject("total_amount")).thenReturn(new BigDecimal("1000.00"));
        lenient().when(resultSet.getObject("paid_amount")).thenReturn(new BigDecimal("1000.00"));
        lenient().when(resultSet.getObject("discount")).thenReturn(BigDecimal.ZERO);
        lenient().when(resultSet.getObject("total_tax")).thenReturn(new BigDecimal("50.00"));
        lenient().when(resultSet.getString("status")).thenReturn("paid");
        lenient().when(resultSet.getString("fulfillment_status")).thenReturn("delivered");
        lenient().when(resultSet.getLong("customer_id")).thenReturn(10L);
        lenient().when(resultSet.wasNull()).thenReturn(false);
        lenient().when(resultSet.getString("customer_name")).thenReturn("John Doe");

        Sale sale = mapper.map(resultSet);

        assertNotNull(sale);
        assertEquals(1L, sale.id());
        assertEquals("INV-001", sale.invoiceNumber());
        assertEquals(LocalDateTime.of(2023, 10, 15, 14, 30), sale.saleDate());
        assertEquals(new BigDecimal("1000.00"), sale.totalAmount());
        assertEquals("paid", sale.status());
        assertEquals(10L, sale.customerId());
        assertEquals("John Doe", sale.customerName());
    }

    @Test
    @DisplayName("Should handle nullable and missing columns in SaleMapper")
    void mapSale_nulls_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("invoice_number")).thenReturn("INV-001");
        lenient().when(resultSet.wasNull()).thenReturn(true); // For customer_id etc
        lenient().when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            if (col.equals("customer_name")) throw new SQLException("Column not found");
            return null;
        });

        Sale sale = mapper.map(resultSet);

        assertNotNull(sale);
        assertNull(sale.customerId());
        assertNull(sale.customerName());
    }
}
