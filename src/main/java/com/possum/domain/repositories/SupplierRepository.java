package com.possum.domain.repositories;

import com.possum.domain.model.PaymentPolicy;
import com.possum.domain.model.Supplier;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SupplierFilter;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository {
    PagedResult<Supplier> getAllSuppliers(SupplierFilter filter);

    Optional<Supplier> findSupplierById(long id);

    long createSupplier(Supplier supplier);

    int updateSupplier(long id, Supplier supplier);

    int deleteSupplier(long id);

    List<PaymentPolicy> getPaymentPolicies();
    long createPaymentPolicy(String name, int daysToPay, String description);
    int updatePaymentPolicy(long id, String name, int daysToPay, String description);
    int deletePaymentPolicy(long id);
}
