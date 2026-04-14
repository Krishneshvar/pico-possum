package com.possum.domain.repositories;

import com.possum.domain.model.TaxExemption;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaxExemptionRepository {
    Optional<TaxExemption> findById(Long id);
    List<TaxExemption> findByCustomerId(Long customerId);
    Optional<TaxExemption> findActiveExemption(Long customerId, LocalDateTime asOf);
    TaxExemption save(TaxExemption exemption);
    void delete(Long id);
}
