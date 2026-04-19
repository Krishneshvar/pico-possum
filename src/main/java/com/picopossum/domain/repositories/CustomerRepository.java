package com.picopossum.domain.repositories;

import com.picopossum.domain.model.Customer;
import com.picopossum.shared.dto.CustomerFilter;
import com.picopossum.shared.dto.PagedResult;

import java.util.Optional;

public interface CustomerRepository {
    PagedResult<Customer> findCustomers(CustomerFilter filter);

    Optional<Customer> findCustomerById(long id);

    Optional<Customer> insertCustomer(String name, String phone, String email, String address,
                                       String customerType);

    Optional<Customer> updateCustomerById(long id, String name, String phone, String email, String address,
                                          String customerType);

    boolean softDeleteCustomer(long id);
}
