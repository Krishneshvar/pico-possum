package com.possum.persistence.mappers;

import com.possum.domain.model.Transaction;
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
class TransactionMapperTest {

    @Mock private ResultSet resultSet;
    private final TransactionMapper mapper = new TransactionMapper();

    @Test
    @DisplayName("Should map ResultSet to Transaction object properly")
    void mapTransaction_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getObject("amount")).thenReturn(new BigDecimal("500.00"));
        lenient().when(resultSet.getString("type")).thenReturn("payment");
        lenient().when(resultSet.getLong("payment_method_id")).thenReturn(2L);
        lenient().when(resultSet.getString("payment_method_name")).thenReturn("Credit Card");
        lenient().when(resultSet.getString("status")).thenReturn("completed");
        lenient().when(resultSet.getString("transaction_date")).thenReturn("2023-10-15 14:30:00");
        lenient().when(resultSet.getString("invoice_number")).thenReturn("INV-123");
        lenient().when(resultSet.getString("customer_name")).thenReturn("Jane Smith");
        lenient().when(resultSet.wasNull()).thenReturn(false);

        Transaction tx = mapper.map(resultSet);

        assertNotNull(tx);
        assertEquals(1L, tx.id());
        assertEquals(new BigDecimal("500.00"), tx.amount());
        assertEquals("payment", tx.type());
        assertEquals(2L, tx.paymentMethodId());
        assertEquals("Credit Card", tx.paymentMethodName());
        assertEquals("completed", tx.status());
        assertEquals(LocalDateTime.of(2023, 10, 15, 14, 30), tx.transactionDate());
        assertEquals("INV-123", tx.invoiceNumber());
        assertEquals("Jane Smith", tx.customerName());
        assertNull(tx.supplierName());
    }

    @Test
    @DisplayName("Should handle missing optional columns in TransactionMapper")
    void mapTransaction_missingCols_success() throws SQLException {
        lenient().when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            if (col.equals("payment_method_name")) throw new SQLException("Column missing");
            return null;
        });
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("type")).thenReturn("refund");

        Transaction tx = mapper.map(resultSet);

        assertNotNull(tx);
        assertEquals("refund", tx.type());
        assertNull(tx.paymentMethodName());
    }
}
