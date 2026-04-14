package com.possum.persistence.mappers;

import com.possum.domain.model.Customer;
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
class CustomerMapperTest {

    @Mock private ResultSet resultSet;
    private final CustomerMapper mapper = new CustomerMapper();

    @Test
    @DisplayName("Should map ResultSet to Customer properly")
    void mapCustomer_success() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getString("name")).thenReturn("John Customer");
        when(resultSet.getString("phone")).thenReturn("1234567890");
        when(resultSet.getString("email")).thenReturn("john@example.com");
        when(resultSet.getString("address")).thenReturn("123 Street");
        when(resultSet.getString("customer_type")).thenReturn("regular");
        when(resultSet.getInt("is_tax_exempt")).thenReturn(1);
        when(resultSet.getString("created_at")).thenReturn("2023-01-01 10:00:00");

        Customer customer = mapper.map(resultSet);

        assertNotNull(customer);
        assertEquals(1L, customer.id());
        assertEquals("John Customer", customer.name());
        assertEquals("john@example.com", customer.email().trim());
        assertEquals("regular", customer.customerType());
        assertTrue(customer.isTaxExempt());
        assertEquals(LocalDateTime.of(2023, 1, 1, 10, 0), customer.createdAt());
    }
}
