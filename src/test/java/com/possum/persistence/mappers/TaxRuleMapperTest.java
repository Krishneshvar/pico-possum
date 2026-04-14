package com.possum.persistence.mappers;

import com.possum.domain.model.TaxRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxRuleMapperTest {

    @Mock private ResultSet resultSet;
    private final TaxRuleMapper mapper = new TaxRuleMapper();

    @Test
    @DisplayName("Should map ResultSet to TaxRule object properly")
    void mapTaxRule_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getLong("tax_profile_id")).thenReturn(10L);
        lenient().when(resultSet.getLong("tax_category_id")).thenReturn(5L);
        lenient().when(resultSet.getString("rule_scope")).thenReturn("invoice");
        lenient().when(resultSet.getObject("min_price")).thenReturn(new BigDecimal("0.00"));
        lenient().when(resultSet.getObject("max_price")).thenReturn(new BigDecimal("1000.00"));
        lenient().when(resultSet.getObject("rate_percent")).thenReturn(new BigDecimal("18.00"));
        lenient().when(resultSet.getInt("compound")).thenReturn(0);
        lenient().when(resultSet.getInt("priority")).thenReturn(1);
        lenient().when(resultSet.getString("valid_from")).thenReturn("2023-01-01");
        lenient().when(resultSet.getString("valid_to")).thenReturn("2024-12-31");
        lenient().when(resultSet.getString("category_name")).thenReturn("Luxury");
        lenient().when(resultSet.wasNull()).thenReturn(false);

        TaxRule rule = mapper.map(resultSet);

        assertNotNull(rule);
        assertEquals(1L, rule.id());
        assertEquals(10L, rule.taxProfileId());
        assertEquals(5L, rule.taxCategoryId());
        assertEquals("invoice", rule.ruleScope());
        assertEquals(new BigDecimal("18.00"), rule.ratePercent());
        assertFalse(rule.compound());
        assertEquals(1, rule.priority());
        assertEquals(LocalDate.of(2023, 1, 1), rule.validFrom());
        assertEquals(LocalDate.of(2024, 12, 31), rule.validTo());
        assertEquals("Luxury", rule.categoryName());
    }

    @Test
    @DisplayName("Should handle nullable and missing columns in TaxRuleMapper")
    void mapTaxRule_nulls_success() throws SQLException {
        lenient().when(resultSet.getString(anyString())).thenReturn(null);
        lenient().when(resultSet.getLong(anyString())).thenReturn(0L);
        lenient().when(resultSet.getObject(anyString())).thenReturn(null);
        lenient().when(resultSet.getInt(anyString())).thenReturn(0);
        lenient().when(resultSet.wasNull()).thenReturn(true);
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getLong("tax_profile_id")).thenReturn(10L);

        TaxRule rule = mapper.map(resultSet);

        assertNotNull(rule);
        assertNull(rule.taxCategoryId());
        assertNull(rule.categoryName());
    }
}
