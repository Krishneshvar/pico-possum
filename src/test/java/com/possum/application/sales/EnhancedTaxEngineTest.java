package com.possum.application.sales;

import com.possum.application.sales.config.TaxConfiguration;
import com.possum.application.sales.config.TaxRoundingStrategy;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnhancedTaxEngineTest {
    private TaxRepository taxRepository;
    private JsonService jsonService;
    private EnhancedTaxEngine engine;
    private TaxProfile profile;

    @BeforeEach
    void setUp() {
        taxRepository = mock(TaxRepository.class);
        jsonService = mock(JsonService.class);
        
        profile = new TaxProfile(1L, "Default", "US", "CA", "EXCLUSIVE", true, 
                LocalDateTime.now(), LocalDateTime.now());
        
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(profile));
        when(jsonService.toJson(any())).thenReturn("[]");
    }

    @Test
    void testInvoiceLevelRounding() {
        TaxConfiguration config = new TaxConfiguration(
                TaxRoundingStrategy.INVOICE_LEVEL, RoundingMode.HALF_UP, true, true);
        engine = new EnhancedTaxEngine(taxRepository, jsonService, config);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10.5"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        
        engine.init();
        
        TaxableItem item1 = new TaxableItem("Product1", "Variant1", new BigDecimal("10.00"), 1, null, 1L, 1L);
        TaxableItem item2 = new TaxableItem("Product2", "Variant2", new BigDecimal("10.00"), 1, null, 2L, 2L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item1, item2));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("2.10"), result.totalTax());
        assertEquals(new BigDecimal("22.10"), result.grandTotal());
    }

    @Test
    void testItemLevelRounding() {
        TaxConfiguration config = new TaxConfiguration(
                TaxRoundingStrategy.ITEM_LEVEL, RoundingMode.HALF_UP, true, true);
        engine = new EnhancedTaxEngine(taxRepository, jsonService, config);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10.5"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        
        engine.init();
        
        TaxableItem item1 = new TaxableItem("Product1", "Variant1", new BigDecimal("10.00"), 1, null, 1L, 1L);
        TaxableItem item2 = new TaxableItem("Product2", "Variant2", new BigDecimal("10.00"), 1, null, 2L, 2L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item1, item2));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("2.10"), result.totalTax());
    }

    @Test
    void testTaxExemptCustomer() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        
        engine.init();
        
        Customer exemptCustomer = new Customer(1L, "NGO", null, null, null, "ngo", true,
                LocalDateTime.now(), LocalDateTime.now(), null);
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, exemptCustomer);
        
        assertEquals(BigDecimal.ZERO, result.totalTax());
        assertEquals(new BigDecimal("100.00"), result.grandTotal());
    }

    @Test
    void testNegativePriceValidation() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of());
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("-10.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        assertThrows(IllegalArgumentException.class, () -> engine.calculate(invoice, null));
    }

    @Test
    void testZeroQuantityValidation() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of());
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("10.00"), 0, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        assertThrows(IllegalArgumentException.class, () -> engine.calculate(invoice, null));
    }

    @Test
    void testNegativeRateValidation() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("-5"), false, 0, null, null, "Invalid", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        
        assertThrows(IllegalStateException.class, () -> engine.init());
    }

    @Test
    void testCustomerTypeFiltering() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule wholesaleRule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, "wholesale",
                new BigDecimal("5"), false, 0, null, null, "Wholesale Tax", LocalDateTime.now(), LocalDateTime.now());
        TaxRule retailRule = new TaxRule(2L, 1L, null, "ITEM", null, null, null, null, "retail",
                new BigDecimal("10"), false, 0, null, null, "Retail Tax", LocalDateTime.now(), LocalDateTime.now());
        
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(wholesaleRule, retailRule));
        engine.init();
        
        Customer wholesaleCustomer = new Customer(1L, "Wholesale Co", null, null, null, "wholesale", false,
                LocalDateTime.now(), LocalDateTime.now(), null);
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, wholesaleCustomer);
        
        assertEquals(new BigDecimal("5.00"), result.totalTax());
    }

    @Test
    void testDateRangeFiltering() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule futureRule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, LocalDate.now().plusDays(1), null, "Future Tax", 
                LocalDateTime.now(), LocalDateTime.now());
        
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(futureRule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("0.00"), result.totalTax());
    }

    @Test
    void testCompoundTaxCalculation() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule simpleRule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "Base Tax", LocalDateTime.now(), LocalDateTime.now());
        TaxRule compoundRule = new TaxRule(2L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("5"), true, 1, null, null, "Compound Tax", LocalDateTime.now(), LocalDateTime.now());
        
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(simpleRule, compoundRule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("15.50"), result.totalTax());
    }

    @Test
    void testNoActiveProfile() {
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.empty());
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(BigDecimal.ZERO, result.totalTax());
    }

    @Test
    void testInclusivePricing_singleSimpleRule() {
        TaxProfile inclusiveProfile = new TaxProfile(1L, "Inclusive", "US", "CA", "INCLUSIVE", true,
                LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(inclusiveProfile));
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("110.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
        assertEquals(new BigDecimal("110.00"), result.grandTotal());
    }

    @Test
    void testInclusivePricing_multipleSimpleRules() {
        TaxProfile inclusiveProfile = new TaxProfile(1L, "Inclusive", "US", "CA", "INCLUSIVE", true,
                LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(inclusiveProfile));
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule1 = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        TaxRule rule2 = new TaxRule(2L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("5"), false, 1, null, null, "Service Tax", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule1, rule2));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("115.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("15.00"), result.totalTax());
    }

    @Test
    void testInclusivePricing_compoundRules() {
        TaxProfile inclusiveProfile = new TaxProfile(1L, "Inclusive", "US", "CA", "INCLUSIVE", true,
                LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getActiveTaxProfile()).thenReturn(Optional.of(inclusiveProfile));
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule simpleRule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "Base Tax", LocalDateTime.now(), LocalDateTime.now());
        TaxRule compoundRule = new TaxRule(2L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("5"), true, 1, null, null, "Compound Tax", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(simpleRule, compoundRule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("115.50"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertTrue(result.totalTax().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testExclusivePricing_multipleSimpleRules() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule1 = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        TaxRule rule2 = new TaxRule(2L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("5"), false, 1, null, null, "Service Tax", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule1, rule2));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("15.00"), result.totalTax());
        assertEquals(new BigDecimal("115.00"), result.grandTotal());
    }

    @Test
    void testRoundingMode_HALF_DOWN() {
        TaxConfiguration config = new TaxConfiguration(
                TaxRoundingStrategy.ITEM_LEVEL, RoundingMode.HALF_DOWN, true, true);
        engine = new EnhancedTaxEngine(taxRepository, jsonService, config);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10.5"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("10.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertNotNull(result);
    }

    @Test
    void testMinPriceConstraint_match() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", new BigDecimal("50"), null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
    }

    @Test
    void testMinPriceConstraint_mismatch() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", new BigDecimal("150"), null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("0.00"), result.totalTax());
    }

    @Test
    void testMaxPriceConstraint_match() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, new BigDecimal("150"), null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
    }

    @Test
    void testMaxPriceConstraint_mismatch() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, new BigDecimal("50"), null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("0.00"), result.totalTax());
    }

    @Test
    void testMinInvoiceTotalConstraint_match() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, new BigDecimal("50"), null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
    }

    @Test
    void testMaxInvoiceTotalConstraint_match() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, new BigDecimal("150"), null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
    }

    @Test
    void testCustomerTypeConstraint_mismatch() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, "wholesale",
                new BigDecimal("10"), false, 0, null, null, "Wholesale Tax", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        Customer retailCustomer = new Customer(1L, "Retail Co", null, null, null, "retail", false,
                LocalDateTime.now(), LocalDateTime.now(), null);
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, retailCustomer);
        
        assertEquals(new BigDecimal("0.00"), result.totalTax());
    }

    @Test
    void testValidFromConstraint_past() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, LocalDate.now().minusDays(10), null, "VAT",
                LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
    }

    @Test
    void testValidToConstraint_expired() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, LocalDate.now().minusDays(1), "Expired Tax",
                LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("0.00"), result.totalTax());
    }

    @Test
    void testValidToConstraint_valid() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, LocalDate.now().plusDays(10), "Valid Tax",
                LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
    }

    @Test
    void testTaxCategoryId_match() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, 5L, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "Category Tax", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, 5L, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("10.00"), result.totalTax());
    }

    @Test
    void testTaxCategoryId_mismatch() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, 5L, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "Category Tax", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, 3L, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("0.00"), result.totalTax());
    }

    @Test
    void testRulePriorityOrdering() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule lowPriority = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("5"), false, 10, null, null, "Low Priority", LocalDateTime.now(), LocalDateTime.now());
        TaxRule highPriority = new TaxRule(2L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "High Priority", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(lowPriority, highPriority));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = engine.calculate(invoice, null);
        
        assertEquals(new BigDecimal("15.00"), result.totalTax());
    }

    @Test
    void testGetApplicableRules_allConstraintsSatisfied() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        List<TaxRule> applicable = engine.getApplicableRules(item, new BigDecimal("100.00"), null);
        
        assertEquals(1, applicable.size());
    }

    @Test
    void testGetApplicableRules_priceOutOfRange() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", new BigDecimal("200"), null, null, null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        List<TaxRule> applicable = engine.getApplicableRules(item, new BigDecimal("100.00"), null);
        
        assertEquals(0, applicable.size());
    }

    @Test
    void testGetApplicableRules_invoiceTotalOutOfRange() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, new BigDecimal("500"), null, null,
                new BigDecimal("10"), false, 0, null, null, "VAT", LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        List<TaxRule> applicable = engine.getApplicableRules(item, new BigDecimal("100.00"), null);
        
        assertEquals(0, applicable.size());
    }

    @Test
    void testGetApplicableRules_dateRangeExpired() {
        engine = new EnhancedTaxEngine(taxRepository, jsonService);
        
        TaxRule rule = new TaxRule(1L, 1L, null, "ITEM", null, null, null, null, null,
                new BigDecimal("10"), false, 0, null, LocalDate.now().minusDays(1), "Expired",
                LocalDateTime.now(), LocalDateTime.now());
        when(taxRepository.getTaxRulesByProfileId(1L)).thenReturn(List.of(rule));
        engine.init();
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100.00"), 1, null, 1L, 1L);
        List<TaxRule> applicable = engine.getApplicableRules(item, new BigDecimal("100.00"), null);
        
        assertEquals(0, applicable.size());
    }
}
