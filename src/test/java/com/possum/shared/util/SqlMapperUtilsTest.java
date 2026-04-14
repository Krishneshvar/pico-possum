package com.possum.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqlMapperUtilsTest {

    @Mock private ResultSet resultSet;

    @Test
    @DisplayName("Should get BigDecimal from ResultSet")
    void getBigDecimal_success() throws SQLException {
        when(resultSet.getObject("col1")).thenReturn(new BigDecimal("100.50"));
        when(resultSet.getObject("col2")).thenReturn(123.456);
        when(resultSet.getObject("col3")).thenReturn(null);

        assertEquals(new BigDecimal("100.50"), SqlMapperUtils.getBigDecimal(resultSet, "col1"));
        assertEquals(new BigDecimal("123.456"), SqlMapperUtils.getBigDecimal(resultSet, "col2"));
        assertNull(SqlMapperUtils.getBigDecimal(resultSet, "col3"));
    }

    @Test
    @DisplayName("Should map booleans from integers (SQLite style)")
    void getBooleanFromInt_success() throws SQLException {
        when(resultSet.getInt("col1")).thenReturn(1);
        when(resultSet.getInt("col2")).thenReturn(0);
        when(resultSet.getInt("col3")).thenReturn(0);
        when(resultSet.wasNull()).thenReturn(false, false, true);

        assertTrue(SqlMapperUtils.getBooleanFromInt(resultSet, "col1"));
        assertFalse(SqlMapperUtils.getBooleanFromInt(resultSet, "col2"));
        assertNull(SqlMapperUtils.getBooleanFromInt(resultSet, "col3"));
    }

    @Test
    @DisplayName("Should extract LocalDateTime from SQLite strings")
    void getLocalDateTime_success() throws SQLException {
        when(resultSet.getString("col1")).thenReturn("2023-10-15 14:30:00");
        when(resultSet.getString("col2")).thenReturn("2023-10-15");
        when(resultSet.getString("col3")).thenReturn("");
        when(resultSet.getString("col4")).thenReturn(null);

        assertEquals(LocalDateTime.of(2023, 10, 15, 14, 30, 0), SqlMapperUtils.getLocalDateTime(resultSet, "col1"));
        assertEquals(LocalDateTime.of(2023, 10, 15, 0, 0, 0), SqlMapperUtils.getLocalDateTime(resultSet, "col2"));
        assertNull(SqlMapperUtils.getLocalDateTime(resultSet, "col3"));
        assertNull(SqlMapperUtils.getLocalDateTime(resultSet, "col4"));
    }

    @Test
    @DisplayName("Should extract LocalDate from ResultSet")
    void getLocalDate_success() throws SQLException {
        when(resultSet.getString("col1")).thenReturn("2023-10-15");
        when(resultSet.getString("col2")).thenReturn(null);

        assertEquals(LocalDate.of(2023, 10, 15), SqlMapperUtils.getLocalDate(resultSet, "col1"));
        assertNull(SqlMapperUtils.getLocalDate(resultSet, "col2"));
    }

    @Test
    @DisplayName("Should fallback to timestamp for getLocalDateTime if parsing fails")
    void getLocalDateTime_fallback_success() throws SQLException {
        when(resultSet.getString("col1")).thenReturn("invalid date string");
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2023, 5, 20, 11, 45));
        when(resultSet.getTimestamp("col1")).thenReturn(ts);

        assertEquals(ts.toLocalDateTime(), SqlMapperUtils.getLocalDateTime(resultSet, "col1"));
    }
}
