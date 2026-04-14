package com.possum.ui.people;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.people.CustomerService;
import com.possum.domain.model.Customer;
import com.possum.shared.dto.CustomerFilter;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.PaginationBar;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomersControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private CustomerService customerService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    @org.mockito.InjectMocks
    private CustomersController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("customers:view", "customers:manage")));
        setField(controller, "paginationBar", paginationBar);
        setField(controller, "filterBar", filterBar);
        lenient().when(paginationBar.getCurrentPage()).thenReturn(0);
        lenient().when(paginationBar.getPageSize()).thenReturn(25);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = com.possum.ui.common.controllers.AbstractCrudController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch customers with filters")
    void fetchData_withFilters_success() {
        CustomerFilter filter = new CustomerFilter("John", null, null, 0, 20, "name", "ASC");
        List<Customer> customers = List.of(
            createTestCustomer(1L, "John Doe", "1234567890", "john@example.com"),
            createTestCustomer(2L, "John Smith", "0987654321", "smith@example.com")
        );
        PagedResult<Customer> pagedResult = new PagedResult<>(customers, 2, 1, 0, 20);
        when(customerService.getCustomers(any(CustomerFilter.class))).thenReturn(pagedResult);

        // Call the controller method
        PagedResult<Customer> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        assertTrue(result.items().stream().allMatch(c -> c.name().contains("John")));
        verify(customerService).getCustomers(any(CustomerFilter.class));
    }

    @Test
    @DisplayName("Should delete customer successfully")
    void deleteEntity_success() throws Exception {
        Long customerId = 1L;
        Customer customer = createTestCustomer(customerId, "John Doe", "1234567890", "john@example.com");
        doNothing().when(customerService).deleteCustomer(customerId);

        // Call the controller method
        controller.deleteEntity(customer);

        verify(customerService).deleteCustomer(customerId);
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        CustomerFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(1, filter.page()); // buildFilter starts from page 1
        assertEquals(25, filter.limit());
        assertEquals("name", filter.sortBy());
        assertEquals("ASC", filter.sortOrder());
    }

    @Test
    @DisplayName("Should get entity name and singular name")
    void getName_success() {
        assertEquals("customers", controller.getEntityName());
        assertEquals("Customer", controller.getEntityNameSingular());
    }

    private Customer createTestCustomer(Long id, String name, String phone, String email) {
        return new Customer(id, name, phone, email, "123 Main St", "retail", false, null, null, null);
    }
}
