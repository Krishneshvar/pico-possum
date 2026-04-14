package com.possum.persistence.mappers;

import com.possum.domain.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductMapperTest {

    @Mock private ResultSet resultSet;
    private final ProductMapper mapper = new ProductMapper();

    @Test
    @DisplayName("Should map ResultSet to Product object properly")
    void mapProduct_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("name")).thenReturn("Cool Widget");
        lenient().when(resultSet.getString("description")).thenReturn("A widget that is cool");
        lenient().when(resultSet.getLong("category_id")).thenReturn(5L);
        lenient().when(resultSet.getString("category_name")).thenReturn("Electronics");
        lenient().when(resultSet.getString("status")).thenReturn("active");
        lenient().when(resultSet.getInt("stock")).thenReturn(100);
        lenient().when(resultSet.wasNull()).thenReturn(false);
        lenient().when(resultSet.getString("created_at")).thenReturn("2023-01-01 10:00:00");

        Product product = mapper.map(resultSet);

        assertNotNull(product);
        assertEquals(1L, product.id());
        assertEquals("Cool Widget", product.name());
        assertEquals(100, product.stock());
        assertEquals(LocalDateTime.of(2023, 1, 1, 10, 0, 0), product.createdAt());
    }

    @Test
    @DisplayName("Should handle nullable and missing columns in ProductMapper")
    void mapProduct_nulls_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("name")).thenReturn("Product X");
        lenient().when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            if (col.equals("category_name")) throw new SQLException("Column not found");
            return null;
        });
        lenient().when(resultSet.wasNull()).thenReturn(true);

        Product product = mapper.map(resultSet);

        assertNotNull(product);
        assertNull(product.categoryId());
        assertNull(product.categoryName());
        assertNull(product.stock());
    }
}
