package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.Sale;
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
        lenient().when(resultSet.getLong(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            return switch (col) {
                case "id" -> 1L;
                case "customer_id" -> 10L;
                case "payment_method_id" -> 2L;
                default -> 0L;
            };
        });
        
        lenient().when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            return switch (col) {
                case "invoice_number" -> "INV-001";
                case "sale_date" -> "2023-10-15 14:30:00";
                case "status" -> "paid";
                case "fulfillment_status" -> "delivered";
                case "customer_name" -> "John Doe";
                case "customer_phone" -> "123456";
                case "customer_email" -> "john@example.com";
                case "biller_name" -> "System Admin";
                case "payment_method_name" -> "Cash";
                case "invoice_id" -> "INV-ID-001";
                default -> null;
            };
        });
        
        lenient().when(resultSet.getObject(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            return switch (col) {
                case "total_amount" -> new BigDecimal("1000.00");
                case "paid_amount" -> new BigDecimal("1000.00");
                case "discount" -> BigDecimal.ZERO;
                default -> null;
            };
        });
        
        lenient().when(resultSet.wasNull()).thenReturn(false);

        Sale sale = mapper.map(resultSet);

        assertNotNull(sale);
        assertEquals(1L, sale.id());
        assertEquals("INV-001", sale.invoiceNumber());
        assertEquals(LocalDateTime.of(2023, 10, 15, 14, 30), sale.saleDate());
        assertEquals(new BigDecimal("1000.00"), sale.totalAmount());
        assertEquals("paid", sale.status());
        assertEquals(10L, sale.customerId());
        assertEquals("John Doe", sale.customerName());
        assertEquals("System Admin", sale.billerName());
        assertEquals(2L, sale.paymentMethodId());
        assertEquals("Cash", sale.paymentMethodName());
    }

    @Test
    @DisplayName("Should handle nullable columns in SaleMapper")
    void mapSale_nulls_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.wasNull()).thenReturn(true);
        lenient().when(resultSet.getString(anyString())).thenReturn(null);
        lenient().when(resultSet.getObject(anyString())).thenReturn(null);

        Sale sale = mapper.map(resultSet);

        assertNotNull(sale);
        assertNull(sale.customerId());
        assertNull(sale.customerName());
        assertNull(sale.paymentMethodId());
    }
}
