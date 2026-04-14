package com.possum.domain.repositories;

import com.possum.domain.model.Customer;
import com.possum.shared.dto.CustomerFilter;
import com.possum.shared.dto.PagedResult;

import java.util.Optional;

public interface CustomerRepository {
    PagedResult<Customer> findCustomers(CustomerFilter filter);

    Optional<Customer> findCustomerById(long id);

    Optional<Customer> insertCustomer(String name, String phone, String email, String address,
                                       String customerType, Boolean isTaxExempt);

    Optional<Customer> updateCustomerById(long id, String name, String phone, String email, String address,
                                          String customerType, Boolean isTaxExempt);

    boolean softDeleteCustomer(long id);
}
