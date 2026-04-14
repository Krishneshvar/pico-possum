package com.possum.domain.services;

import com.possum.application.sales.dto.TaxCalculationResult;
import com.possum.application.sales.dto.TaxableInvoice;
import com.possum.domain.model.Customer;

public interface TaxCalculator {
    TaxCalculationResult calculate(TaxableInvoice invoice, Customer customer);
}
