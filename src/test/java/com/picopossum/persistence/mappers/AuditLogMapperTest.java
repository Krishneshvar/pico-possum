package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.AuditLog;
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
class AuditLogMapperTest {

    @Mock private ResultSet resultSet;
    private final AuditLogMapper mapper = new AuditLogMapper();

    @Test
    @DisplayName("Should map ResultSet to AuditLog object properly")
    void mapAuditLog_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("action")).thenReturn("UPDATE");
        lenient().when(resultSet.getString("table_name")).thenReturn("products");
        lenient().when(resultSet.getLong("row_id")).thenReturn(100L);
        lenient().when(resultSet.getString("old_data")).thenReturn("{\"price\": 10}");
        lenient().when(resultSet.getString("new_data")).thenReturn("{\"price\": 15}");
        lenient().when(resultSet.getString("event_details")).thenReturn("Price updated");
        lenient().when(resultSet.getString("severity")).thenReturn("info");
        lenient().when(resultSet.getString("integrity_hash")).thenReturn("HASH123");
        lenient().when(resultSet.wasNull()).thenReturn(false);
        lenient().when(resultSet.getString("created_at")).thenReturn("2023-10-15 14:30:00");

        AuditLog log = mapper.map(resultSet);

        assertNotNull(log);
        assertEquals(1L, log.id());
        assertEquals("UPDATE", log.action());
        assertEquals("products", log.tableName());
        assertEquals(100L, log.rowId());
        assertEquals("info", log.severity());
        assertEquals("HASH123", log.integrityHash());
        assertEquals(LocalDateTime.of(2023, 10, 15, 14, 30), log.createdAt());
    }

    @Test
    @DisplayName("Should handle nulls in AuditLogMapper")
    void mapAuditLog_nulls_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("action")).thenReturn("LOGIN");
        lenient().when(resultSet.getLong("row_id")).thenReturn(0L);
        lenient().when(resultSet.wasNull()).thenReturn(true);
        lenient().when(resultSet.getString("created_at")).thenReturn("2023-10-15 14:30:00");

        AuditLog log = mapper.map(resultSet);

        assertNotNull(log);
        assertNull(log.rowId());
    }
}
