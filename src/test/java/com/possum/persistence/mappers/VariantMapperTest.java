package com.possum.persistence.mappers;

import com.possum.domain.model.Variant;
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
class VariantMapperTest {

    @Mock private ResultSet resultSet;
    private final VariantMapper mapper = new VariantMapper();

    @Test
    @DisplayName("Should map ResultSet to Variant object properly")
    void mapVariant_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getLong("product_id")).thenReturn(10L);
        lenient().when(resultSet.getString("product_name")).thenReturn("Cool Product");
        lenient().when(resultSet.getString("name")).thenReturn("Standard");
        lenient().when(resultSet.getString("sku")).thenReturn("SKU-001");
        lenient().when(resultSet.getObject("price")).thenReturn(new BigDecimal("100.00"));
        lenient().when(resultSet.getObject("cost_price")).thenReturn(new BigDecimal("80.00"));
        lenient().when(resultSet.getInt("stock_alert_cap")).thenReturn(5);
        lenient().when(resultSet.getInt("is_default")).thenReturn(1);
        lenient().when(resultSet.getInt("stock")).thenReturn(50);
        lenient().when(resultSet.getString("status")).thenReturn("active");
        lenient().when(resultSet.wasNull()).thenReturn(false);
        lenient().when(resultSet.getString("created_at")).thenReturn("2023-01-01 10:00:00");

        Variant variant = mapper.map(resultSet);

        assertNotNull(variant);
        assertEquals(1L, variant.id());
        assertEquals(10L, variant.productId());
        assertEquals("Cool Product", variant.productName());
        assertEquals("Standard", variant.name());
        assertEquals("SKU-001", variant.sku());
        assertEquals(new BigDecimal("100.00"), variant.price());
        assertEquals(5, variant.stockAlertCap());
        assertTrue(variant.defaultVariant());
        assertEquals(50, variant.stock());
        assertEquals(LocalDateTime.of(2023, 1, 1, 10, 0, 0), variant.createdAt());
    }

    @Test
    @DisplayName("Should handle nullable and missing columns in VariantMapper")
    void mapVariant_nulls_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getLong("product_id")).thenReturn(10L);
        lenient().when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            if (col.equals("product_name")) throw new SQLException("Column not found");
            return null;
        });
        lenient().when(resultSet.wasNull()).thenReturn(true);

        Variant variant = mapper.map(resultSet);

        assertNotNull(variant);
        assertNull(variant.productName());
        assertNull(variant.stockAlertCap());
        assertNull(variant.stock());
    }
}
