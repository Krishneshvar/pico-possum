package com.picopossum.application.people;

import com.picopossum.domain.model.Customer;
import com.picopossum.domain.repositories.CustomerRepository;
import com.picopossum.shared.dto.CustomerFilter;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.util.DomainValidators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CustomerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public PagedResult<Customer> getCustomers(CustomerFilter filter) {
        return customerRepository.findCustomers(filter);
    }

    public Optional<Customer> getCustomerById(long id) {
        return customerRepository.findCustomerById(id);
    }

    public Customer createCustomer(String name, String phone, String email, String address) {
        return createCustomer(name, phone, email, address, null);
    }

    public Customer createCustomer(String name, String phone, String email, String address,
                                    String customerType) {
        if (name == null || name.isBlank()) throw new com.picopossum.domain.exceptions.ValidationException("Customer name is required");
        if (phone != null && !phone.isBlank() && !DomainValidators.PHONE.matcher(phone.trim()).matches()) throw new com.picopossum.domain.exceptions.ValidationException("Invalid phone number format");
        if (email != null && !email.isBlank() && !DomainValidators.EMAIL.matcher(email.trim()).matches()) throw new com.picopossum.domain.exceptions.ValidationException("Invalid email address format");
        return customerRepository.insertCustomer(name, phone, email, address, customerType)
                .orElseThrow(() -> new com.picopossum.domain.exceptions.DomainException("Failed to create customer"));
    }

    public Customer updateCustomer(long id, String name, String phone, String email, String address) {
        return updateCustomer(id, name, phone, email, address, null);
    }

    public Customer updateCustomer(long id, String name, String phone, String email, String address,
                                    String customerType) {
        if (name == null || name.isBlank()) throw new com.picopossum.domain.exceptions.ValidationException("Customer name is required");
        if (phone != null && !phone.isBlank() && !DomainValidators.PHONE.matcher(phone.trim()).matches()) throw new com.picopossum.domain.exceptions.ValidationException("Invalid phone number format");
        if (email != null && !email.isBlank() && !DomainValidators.EMAIL.matcher(email.trim()).matches()) throw new com.picopossum.domain.exceptions.ValidationException("Invalid email address format");
        return customerRepository.updateCustomerById(id, name, phone, email, address, customerType)
                .orElseThrow(() -> new com.picopossum.domain.exceptions.NotFoundException("Customer not found: " + id));
    }

    public void deleteCustomer(long id) {
        if (!customerRepository.softDeleteCustomer(id)) {
            throw new com.picopossum.domain.exceptions.NotFoundException("Customer not found: " + id);
        }
    }

    /**
     * Resolves a customer by phone or creates a new one if not found.
     * Core logic extracted from UI to maintain SOC and reliability.
     */
    public Customer resolveOrCreateCustomer(String name, String phone, String email, String address) {
        if (phone != null && !phone.isBlank()) {
            Optional<Customer> existing = customerRepository.findCustomers(
                    new CustomerFilter(phone, 1, 1, 0, 1, "name", "ASC")
            ).items().stream().filter(c -> phone.equals(c.phone())).findFirst();
            
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        
        if (name == null || name.isBlank()) {
            return null; // Cannot create without name
        }
        
        LOGGER.info("Creating new customer from POS flow: {} ({})", name, phone);
        return createCustomer(name, phone, email, address);
    }
}
