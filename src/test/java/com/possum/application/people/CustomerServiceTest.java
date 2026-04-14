package com.possum.application.people;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.domain.exceptions.AuthorizationException;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Customer;
import com.possum.domain.repositories.CustomerRepository;
import com.possum.shared.dto.CustomerFilter;
import com.possum.shared.dto.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
        AuthContext.setCurrentUser(new AuthUser(1L, "Admin", "admin", List.of(), List.of("customers.manage")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch customers with pagination")
    void getCustomers_success() {
        CustomerFilter filter = new CustomerFilter(null, null, null, 1, 10, "name", "asc");
        PagedResult<Customer> page = new PagedResult<>(List.of(), 0, 0, 1, 10);
        when(customerRepository.findCustomers(filter)).thenReturn(page);

        PagedResult<Customer> result = customerService.getCustomers(filter);

        assertNotNull(result);
        assertEquals(0, result.totalCount());
    }

    @Test
    @DisplayName("Should fetch customer by ID")
    void getCustomerById_success() {
        Customer c = new Customer(1L, "John Doe", "1234567890", "john@example.com", "Address", "regular", false, LocalDateTime.now(), null, null);
        when(customerRepository.findCustomerById(1L)).thenReturn(Optional.of(c));

        Optional<Customer> result = customerService.getCustomerById(1L);
        assertTrue(result.isPresent());
        assertEquals("John Doe", result.get().name());
    }

    @Test
    @DisplayName("Should create customer when valid")
    void createCustomer_success() {
        Customer c = new Customer(1L, "Jane Doe", "9876543210", "jane@example.com", "Address", "vip", true, LocalDateTime.now(), null, null);
        when(customerRepository.insertCustomer("Jane Doe", "9876543210", "jane@example.com", "Address", "vip", true)).thenReturn(Optional.of(c));

        Customer result = customerService.createCustomer("Jane Doe", "9876543210", "jane@example.com", "Address", "vip", true);
        assertEquals(1L, result.id());
    }

    @Test
    @DisplayName("Should throw validation error if phone format is bad")
    void createCustomer_badPhone_fail() {
        assertThrows(ValidationException.class, () -> customerService.createCustomer("Jane Doe", "invalid_phone", "jane@example.com", "Address", "vip", true));
    }

    @Test
    @DisplayName("Should throw validation error if email format is bad")
    void createCustomer_badEmail_fail() {
        assertThrows(ValidationException.class, () -> customerService.createCustomer("Jane Doe", "9876543210", "invalid_email", "Address", "vip", true));
    }

    @Test
    @DisplayName("Should throw auth error if missing permission")
    void createCustomer_unauthorized_fail() {
        AuthContext.setCurrentUser(new AuthUser(1L, "User", "user", List.of(), List.of()));
        assertThrows(AuthorizationException.class, () -> customerService.createCustomer("Jane Doe", "9876543210", "jane@example.com", "Address", "vip", true));
    }

    @Test
    @DisplayName("Should update customer successfully")
    void updateCustomer_success() {
        Customer c = new Customer(1L, "Update Doe", "9876543210", "jane@example.com", "Address", "vip", true, LocalDateTime.now(), null, null);
        when(customerRepository.updateCustomerById(1L, "Update Doe", "9876543210", "jane@example.com", "Address", "vip", true)).thenReturn(Optional.of(c));

        Customer result = customerService.updateCustomer(1L, "Update Doe", "9876543210", "jane@example.com", "Address", "vip", true);
        assertEquals("Update Doe", result.name());
    }

    @Test
    @DisplayName("Should throw NotFound if update target missing")
    void updateCustomer_notFound_fail() {
        when(customerRepository.updateCustomerById(1L, "Update Doe", "9876543210", "jane@example.com", "Address", "vip", true)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> customerService.updateCustomer(1L, "Update Doe", "9876543210", "jane@example.com", "Address", "vip", true));
    }

    @Test
    @DisplayName("Should delete customer successfully")
    void deleteCustomer_success() {
        when(customerRepository.softDeleteCustomer(1L)).thenReturn(true);
        assertDoesNotThrow(() -> customerService.deleteCustomer(1L));
    }

    @Test
    @DisplayName("Should throw NotFound if delete target missing")
    void deleteCustomer_notFound_fail() {
        when(customerRepository.softDeleteCustomer(1L)).thenReturn(false);
        assertThrows(NotFoundException.class, () -> customerService.deleteCustomer(1L));
    }
}
