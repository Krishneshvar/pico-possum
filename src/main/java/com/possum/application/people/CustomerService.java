package com.possum.application.people;

import com.possum.domain.model.Customer;
import com.possum.domain.repositories.CustomerRepository;
import com.possum.shared.dto.CustomerFilter;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.util.DomainValidators;

import java.util.Optional;

public class CustomerService {
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
        if (name == null || name.isBlank()) throw new com.possum.domain.exceptions.ValidationException("Customer name is required");
        if (phone != null && !phone.isBlank() && !DomainValidators.PHONE.matcher(phone.trim()).matches()) throw new com.possum.domain.exceptions.ValidationException("Invalid phone number format");
        if (email != null && !email.isBlank() && !DomainValidators.EMAIL.matcher(email.trim()).matches()) throw new com.possum.domain.exceptions.ValidationException("Invalid email address format");
        return customerRepository.insertCustomer(name, phone, email, address, customerType)
                .orElseThrow(() -> new com.possum.domain.exceptions.DomainException("Failed to create customer"));
    }

    public Customer updateCustomer(long id, String name, String phone, String email, String address) {
        return updateCustomer(id, name, phone, email, address, null);
    }

    public Customer updateCustomer(long id, String name, String phone, String email, String address,
                                    String customerType) {
        if (name == null || name.isBlank()) throw new com.possum.domain.exceptions.ValidationException("Customer name is required");
        if (phone != null && !phone.isBlank() && !DomainValidators.PHONE.matcher(phone.trim()).matches()) throw new com.possum.domain.exceptions.ValidationException("Invalid phone number format");
        if (email != null && !email.isBlank() && !DomainValidators.EMAIL.matcher(email.trim()).matches()) throw new com.possum.domain.exceptions.ValidationException("Invalid email address format");
        return customerRepository.updateCustomerById(id, name, phone, email, address, customerType)
                .orElseThrow(() -> new com.possum.domain.exceptions.NotFoundException("Customer not found: " + id));
    }

    public void deleteCustomer(long id) {
        if (!customerRepository.softDeleteCustomer(id)) {
            throw new com.possum.domain.exceptions.NotFoundException("Customer not found: " + id);
        }
    }
}
