package com.possum.persistence.mappers;

import com.possum.domain.model.Supplier;
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
class SupplierMapperTest {

    @Mock private ResultSet resultSet;
    private final SupplierMapper mapper = new SupplierMapper();

    @Test
    @DisplayName("Should map ResultSet to Supplier properly")
    void mapSupplier_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("name")).thenReturn("Mega Corp");
        lenient().when(resultSet.getString("contact_person")).thenReturn("Jane Manager");
        lenient().when(resultSet.getString("phone")).thenReturn("555-0199");
        lenient().when(resultSet.getString("email")).thenReturn("jane@megacorp.com");
        lenient().when(resultSet.getString("gstin")).thenReturn("27AAACG0000A1Z5");
        lenient().when(resultSet.getLong("payment_policy_id")).thenReturn(3L);
        lenient().when(resultSet.wasNull()).thenReturn(false);
        lenient().when(resultSet.getString("created_at")).thenReturn("2023-01-01 10:00:00");

        Supplier supplier = mapper.map(resultSet);

        assertNotNull(supplier);
        assertEquals(1L, supplier.id());
        assertEquals("Mega Corp", supplier.name());
        assertEquals("Jane Manager", supplier.contactPerson());
        assertEquals("27AAACG0000A1Z5", supplier.gstin());
        assertEquals(3L, supplier.paymentPolicyId());
        assertEquals(LocalDateTime.of(2023, 1, 1, 10, 0), supplier.createdAt());
    }

    @Test
    @DisplayName("Should handle missing optional columns in Supplier")
    void mapSupplier_nulls_success() throws SQLException {
        lenient().when(resultSet.getLong("id")).thenReturn(1L);
        lenient().when(resultSet.getString("name")).thenReturn("Small Shop");
        lenient().when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String col = invocation.getArgument(0);
            if (col.equals("payment_policy_name")) throw new SQLException("Col missing");
            return null;
        });
        lenient().when(resultSet.wasNull()).thenReturn(true);

        Supplier supplier = mapper.map(resultSet);

        assertNotNull(supplier);
        assertNull(supplier.paymentPolicyId());
        assertNull(supplier.paymentPolicyName());
    }
}
