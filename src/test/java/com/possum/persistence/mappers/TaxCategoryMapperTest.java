package com.possum.persistence.mappers;

import com.possum.domain.model.TaxCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxCategoryMapperTest {

    @Mock private ResultSet resultSet;
    private final TaxCategoryMapper mapper = new TaxCategoryMapper();

    @Test
    @DisplayName("Should map ResultSet to TaxCategory properly")
    void mapTaxCategory_success() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getString("name")).thenReturn("GST 18%");
        when(resultSet.getString("description")).thenReturn("Standard GST rate");
        when(resultSet.getInt("product_count")).thenReturn(42);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getString("created_at")).thenReturn("2023-01-01 10:00:00");

        TaxCategory category = mapper.map(resultSet);

        assertNotNull(category);
        assertEquals(1L, category.id());
        assertEquals("GST 18%", category.name());
        assertEquals(42, category.productCount());
        assertEquals(LocalDateTime.of(2023, 1, 1, 10, 0), category.createdAt());
    }

    @Test
    @DisplayName("Should handle missing product_count in TaxCategory")
    void mapTaxCategory_missingCols_success() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getString("name")).thenReturn("GST 5%");
        when(resultSet.getInt("product_count")).thenThrow(new SQLException("Column missing"));

        TaxCategory category = mapper.map(resultSet);

        assertNotNull(category);
        assertEquals("GST 5%", category.name());
        assertNull(category.productCount());
    }
}
