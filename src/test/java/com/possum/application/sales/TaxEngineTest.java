package com.possum.application.sales;

import com.possum.application.sales.dto.TaxCalculationResult;
import com.possum.application.sales.dto.TaxableInvoice;
import com.possum.application.sales.dto.TaxableItem;
import com.possum.domain.model.Customer;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.domain.repositories.TaxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxEngineTest {

    @Mock private TaxRepository taxRepository;
    @Mock private JsonService jsonService;

    private TaxEngine taxEngine;
    private TaxProfile exclusiveProfile;
    private TaxProfile inclusiveProfile;

    @BeforeEach
    void setUp() {
        taxEngine = new TaxEngine(taxRepository, jsonService);
        exclusiveProfile = new TaxProfile(1L, "Exclusive Profile", "US", "EX", "EXCLUSIVE", true, LocalDateTime.now(), LocalDateTime.now());
        inclusiveProfile = new TaxProfile(2L, "Inclusive Profile", "US", "IN", "INCLUSIVE", true, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void calculate_exclusiveTax_simpleRule() {
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(exclusiveProfile));
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(
            new TaxRule(1L, 1L, null, "SCOPE", null, null, null, null, null, BigDecimal.valueOf(10), false, 10, null, null, "VAT", null, null)
        ));
        taxEngine.init();

        TaxableItem item = new TaxableItem("Product", "Variant", BigDecimal.valueOf(100), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));

        TaxCalculationResult result = taxEngine.calculate(invoice, null);

        assertEquals(0, new BigDecimal("10.00").compareTo(result.totalTax()));
        assertEquals(0, new BigDecimal("110.00").compareTo(result.grandTotal()));
    }

    @Test
    void calculate_inclusiveTax_simpleRule() {
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(inclusiveProfile));
        when(taxRepository.getTaxRulesByProfileId(2L)).thenReturn(List.of(
            new TaxRule(2L, 2L, null, "SCOPE", null, null, null, null, null, BigDecimal.valueOf(10), false, 10, null, null, "GST", null, null)
        ));
        taxEngine.init();

        // If price is 110 inclusive of 10% tax, base is 100, tax is 10.
        TaxableItem item = new TaxableItem("Product", "Variant", BigDecimal.valueOf(110), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));

        TaxCalculationResult result = taxEngine.calculate(invoice, null);

        assertEquals(0, new BigDecimal("10.00").compareTo(result.totalTax()));
        assertEquals(0, new BigDecimal("110.00").compareTo(result.grandTotal()));
    }

    @Test
    void calculate_compoundTax_exclusive() {
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(exclusiveProfile));
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(
            new TaxRule(1L, 1L, null, "SCOPE", null, null, null, null, null, BigDecimal.valueOf(10), false, 1, null, null, "VAT", null, null),
            new TaxRule(2L, 1L, null, "SCOPE", null, null, null, null, null, BigDecimal.valueOf(5), true, 2, null, null, "CESS", null, null)
        ));
        taxEngine.init();

        TaxableItem item = new TaxableItem("Product", "Variant", BigDecimal.valueOf(100), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));

        TaxCalculationResult result = taxEngine.calculate(invoice, null);

        assertEquals(0, new BigDecimal("15.50").compareTo(result.totalTax()));
        assertEquals(0, new BigDecimal("115.50").compareTo(result.grandTotal()));
    }

    @Test
    void calculate_taxExemptCustomer_shouldReturnZeroTax() {
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(exclusiveProfile));
        taxEngine.init();

        Customer exemptCustomer = mock(Customer.class);
        when(exemptCustomer.isTaxExempt()).thenReturn(true);

        TaxableItem item = new TaxableItem("Product", "Variant", BigDecimal.valueOf(100), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));

        TaxCalculationResult result = taxEngine.calculate(invoice, exemptCustomer);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalTax()));
        assertEquals(0, new BigDecimal("100.00").compareTo(result.grandTotal()));
    }
}
