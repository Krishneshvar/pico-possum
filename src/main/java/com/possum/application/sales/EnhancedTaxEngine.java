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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnhancedTaxEngine {
    private final TaxRepository taxRepository;
    private final JsonService jsonService;
    private final TaxConfiguration config;
    private TaxProfile profile;
    private List<TaxRule> rules;

    public EnhancedTaxEngine(TaxRepository taxRepository, JsonService jsonService, TaxConfiguration config) {
        this.taxRepository = taxRepository;
        this.jsonService = jsonService;
        this.config = config;
    }

    public EnhancedTaxEngine(TaxRepository taxRepository, JsonService jsonService) {
        this(taxRepository, jsonService, TaxConfiguration.defaultConfig());
    }

    public void init() {
        this.profile = taxRepository.getActiveTaxProfile().orElse(null);
        if (this.profile != null) {
            this.rules = taxRepository.getTaxRulesByProfileId(this.profile.id());
            validateRules();
        } else {
            this.rules = List.of();
        }
    }

    private void validateRules() {
        if (!config.isValidateNegativeRates()) return;
        
        for (TaxRule rule : rules) {
            if (rule.ratePercent() != null && rule.ratePercent().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Tax rule " + rule.id() + " has negative rate: " + rule.ratePercent());
            }
        }
    }

    public TaxCalculationResult calculate(TaxableInvoice invoice, Customer customer) {
        if (profile == null) {
            return zeroTaxResult(invoice);
        }

        if (customer != null && Boolean.TRUE.equals(customer.isTaxExempt())) {
            return zeroTaxResult(invoice);
        }

        validateInvoice(invoice);

        if (config.getRoundingStrategy() == TaxRoundingStrategy.INVOICE_LEVEL) {
            return calculateWithInvoiceLevelRounding(invoice, customer);
        } else {
            return calculateWithItemLevelRounding(invoice, customer);
        }
    }

    private void validateInvoice(TaxableInvoice invoice) {
        for (TaxableItem item : invoice.items()) {
            if (config.isValidateNegativePrices()) {
                if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Item price must be non-negative");
                }
            }
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be positive");
            }
        }
    }

    private TaxCalculationResult calculateWithInvoiceLevelRounding(TaxableInvoice invoice, Customer customer) {
        BigDecimal invoiceTotal = invoice.getSubtotal();
        BigDecimal rawTotalTax = BigDecimal.ZERO;
        List<TaxableItem> updatedItems = new ArrayList<>();

        for (TaxableItem item : invoice.items()) {
            List<TaxRule> applicableRules = getApplicableRules(item, invoiceTotal, customer);
            TaxItemResult result = calculateItemTax(item, applicableRules);

            item.setTaxAmount(result.taxAmount);
            item.setTaxRate(result.taxRate);
            item.setTaxRuleSnapshot(jsonService.toJson(result.snapshot));

            updatedItems.add(item);
            rawTotalTax = rawTotalTax.add(result.taxAmount);
        }

        BigDecimal totalTax = rawTotalTax.setScale(2, config.getRoundingMode());

        BigDecimal grandTotal;
        if ("INCLUSIVE".equalsIgnoreCase(profile.pricingMode())) {
            grandTotal = invoiceTotal;
        } else {
            grandTotal = invoiceTotal.add(totalTax);
        }

        return new TaxCalculationResult(
                updatedItems,
                totalTax,
                grandTotal.setScale(2, config.getRoundingMode())
        );
    }

    private TaxCalculationResult calculateWithItemLevelRounding(TaxableInvoice invoice, Customer customer) {
        BigDecimal invoiceTotal = invoice.getSubtotal();
        BigDecimal totalTax = BigDecimal.ZERO;
        List<TaxableItem> updatedItems = new ArrayList<>();

        for (TaxableItem item : invoice.items()) {
            List<TaxRule> applicableRules = getApplicableRules(item, invoiceTotal, customer);
            TaxItemResult result = calculateItemTax(item, applicableRules);

            BigDecimal roundedItemTax = result.taxAmount.setScale(2, config.getRoundingMode());
            item.setTaxAmount(roundedItemTax);
            item.setTaxRate(result.taxRate);
            item.setTaxRuleSnapshot(jsonService.toJson(result.snapshot));

            updatedItems.add(item);
            totalTax = totalTax.add(roundedItemTax);
        }

        BigDecimal grandTotal;
        if ("INCLUSIVE".equalsIgnoreCase(profile.pricingMode())) {
            grandTotal = invoiceTotal;
        } else {
            grandTotal = invoiceTotal.add(totalTax);
        }

        return new TaxCalculationResult(
                updatedItems,
                totalTax,
                grandTotal.setScale(2, config.getRoundingMode())
        );
    }

    public List<TaxRule> getApplicableRules(TaxableItem item, BigDecimal invoiceTotal, Customer customer) {
        if (rules == null) return List.of();

        LocalDate now = LocalDate.now();
        BigDecimal itemPrice = item.getPrice();

        return rules.stream()
                .filter(rule -> {
                    if (rule.ratePercent() == null) return false;
                    if (config.isValidateNegativeRates() && rule.ratePercent().compareTo(BigDecimal.ZERO) < 0) return false;
                    if (rule.taxCategoryId() != null && !rule.taxCategoryId().equals(item.getTaxCategoryId())) {
                        return false;
                    }
                    if (rule.minPrice() != null && itemPrice.compareTo(rule.minPrice()) < 0) return false;
                    if (rule.maxPrice() != null && itemPrice.compareTo(rule.maxPrice()) > 0) return false;
                    if (rule.minInvoiceTotal() != null && invoiceTotal.compareTo(rule.minInvoiceTotal()) < 0) return false;
                    if (rule.maxInvoiceTotal() != null && invoiceTotal.compareTo(rule.maxInvoiceTotal()) > 0) return false;
                    if (rule.customerType() != null && customer != null && !rule.customerType().equals(customer.customerType())) return false;
                    if (rule.validFrom() != null && rule.validFrom().isAfter(now)) return false;
                    if (rule.validTo() != null && rule.validTo().isBefore(now)) return false;
                    return true;
                })
                .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
                .collect(Collectors.toList());
    }

    private TaxItemResult calculateItemTax(TaxableItem item, List<TaxRule> rules) {
        BigDecimal taxAmount = BigDecimal.ZERO;
        List<Map<String, Object>> snapshot = new ArrayList<>();

        List<TaxRule> simpleRules = rules.stream().filter(r -> !Boolean.TRUE.equals(r.compound())).toList();
        List<TaxRule> compoundRules = rules.stream().filter(r -> Boolean.TRUE.equals(r.compound())).toList();
        List<TaxRule> allRules = new ArrayList<>(simpleRules);
        allRules.addAll(compoundRules);

        BigDecimal itemTotalAmount = item.getLineTotal();

        if ("INCLUSIVE".equalsIgnoreCase(profile.pricingMode())) {
            BigDecimal currentSimTax = BigDecimal.ZERO;
            for (TaxRule rule : allRules) {
                BigDecimal ruleFactor = rule.ratePercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                BigDecimal ruleTax;
                if (!Boolean.TRUE.equals(rule.compound())) {
                    ruleTax = BigDecimal.ONE.multiply(ruleFactor);
                } else {
                    ruleTax = BigDecimal.ONE.add(currentSimTax).multiply(ruleFactor);
                }
                currentSimTax = currentSimTax.add(ruleTax);
            }

            BigDecimal totalFactor = BigDecimal.ONE.add(currentSimTax);
            BigDecimal baseAmount = itemTotalAmount.divide(totalFactor, 10, RoundingMode.HALF_UP);

            BigDecimal currentTotalTax = BigDecimal.ZERO;
            for (TaxRule rule : allRules) {
                BigDecimal ruleFactor = rule.ratePercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                BigDecimal ruleTaxAmount;
                if (!Boolean.TRUE.equals(rule.compound())) {
                    ruleTaxAmount = baseAmount.multiply(ruleFactor);
                } else {
                    ruleTaxAmount = baseAmount.add(currentTotalTax).multiply(ruleFactor);
                }

                snapshot.add(Map.of(
                        "rule_name", getRuleName(rule),
                        "rate", rule.ratePercent(),
                        "amount", ruleTaxAmount.setScale(4, RoundingMode.HALF_UP),
                        "is_compound", Boolean.TRUE.equals(rule.compound())
                ));

                currentTotalTax = currentTotalTax.add(ruleTaxAmount);
            }

            taxAmount = currentTotalTax;
        } else {
            BigDecimal baseAmount = itemTotalAmount;
            BigDecimal currentTotalTax = BigDecimal.ZERO;

            for (TaxRule rule : allRules) {
                BigDecimal ruleFactor = rule.ratePercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                BigDecimal ruleTaxAmount;
                if (!Boolean.TRUE.equals(rule.compound())) {
                    ruleTaxAmount = baseAmount.multiply(ruleFactor);
                } else {
                    ruleTaxAmount = baseAmount.add(currentTotalTax).multiply(ruleFactor);
                }

                snapshot.add(Map.of(
                        "rule_name", getRuleName(rule),
                        "rate", rule.ratePercent(),
                        "amount", ruleTaxAmount.setScale(4, RoundingMode.HALF_UP),
                        "is_compound", Boolean.TRUE.equals(rule.compound())
                ));

                currentTotalTax = currentTotalTax.add(ruleTaxAmount);
            }

            taxAmount = currentTotalTax;
        }

        BigDecimal effectiveRate = itemTotalAmount.compareTo(BigDecimal.ZERO) > 0
                ? taxAmount.divide(itemTotalAmount, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new TaxItemResult(taxAmount, effectiveRate, snapshot);
    }

    private String getRuleName(TaxRule rule) {
        if (rule.categoryName() != null) {
            return rule.categoryName() + " (" + rule.ratePercent() + "%)";
        }
        return "Tax (" + rule.ratePercent() + "%)";
    }

    private TaxCalculationResult zeroTaxResult(TaxableInvoice invoice) {
        List<TaxableItem> updatedItems = new ArrayList<>();
        for (TaxableItem item : invoice.items()) {
            item.setTaxAmount(BigDecimal.ZERO);
            item.setTaxRate(BigDecimal.ZERO);
            item.setTaxRuleSnapshot("[]");
            updatedItems.add(item);
        }
        return new TaxCalculationResult(updatedItems, BigDecimal.ZERO, invoice.getSubtotal());
    }

    private record TaxItemResult(BigDecimal taxAmount, BigDecimal taxRate, List<Map<String, Object>> snapshot) {}
}
