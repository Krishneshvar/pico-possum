package com.possum.domain.services;

import com.possum.application.sales.dto.TaxCalculationResult;
import com.possum.application.sales.dto.TaxableInvoice;
import com.possum.application.sales.dto.TaxableItem;
import com.possum.domain.model.CartItem;
import com.possum.domain.model.Customer;
import com.possum.domain.model.SaleDraft;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class SaleCalculator implements DomainService {
    private final TaxCalculator taxCalculator;

    public SaleCalculator(TaxCalculator taxCalculator) {
        this.taxCalculator = taxCalculator;
    }

    public void recalculate(SaleDraft draft) {
        if (draft.getItems().isEmpty()) {
            draft.setSubtotal(BigDecimal.ZERO);
            draft.setTaxAmount(BigDecimal.ZERO);
            draft.setDiscountTotal(BigDecimal.ZERO);
            draft.setTotal(BigDecimal.ZERO);
            draft.setTotalMrp(BigDecimal.ZERO);
            draft.setTotalPrice(BigDecimal.ZERO);
            return;
        }

        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal mrpTotal = BigDecimal.ZERO;
        BigDecimal priceTotal = BigDecimal.ZERO;

        for (CartItem it : draft.getItems()) {
            it.calculateBasics();
            grossTotal = grossTotal.add(it.getNetLineTotal());
            mrpTotal = mrpTotal.add(it.getVariant().price().multiply(BigDecimal.valueOf(it.getQuantity())));
            priceTotal = priceTotal.add(it.getPricePerUnit().multiply(BigDecimal.valueOf(it.getQuantity())));
        }

        draft.setTotalMrp(mrpTotal);
        draft.setTotalPrice(priceTotal);

        // Distribute overall discount
        BigDecimal overallDiscount = BigDecimal.ZERO;
        if (draft.getOverallDiscountValue().compareTo(BigDecimal.ZERO) > 0) {
            if (draft.isDiscountFixed()) {
                overallDiscount = draft.getOverallDiscountValue();
            } else {
                overallDiscount = grossTotal.multiply(draft.getOverallDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal lineDiscounts = draft.getItems().stream()
                .map(CartItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        draft.setDiscountTotal(lineDiscounts.add(overallDiscount));

        // Tax calculation prep
        List<TaxableItem> txItems = new ArrayList<>();
        BigDecimal distributedDiscount = BigDecimal.ZERO;
        
        for (int i = 0; i < draft.getItems().size(); i++) {
            CartItem it = draft.getItems().get(i);
            BigDecimal itemGlobalDiscount = BigDecimal.ZERO;
            
            if (grossTotal.compareTo(BigDecimal.ZERO) > 0 && overallDiscount.compareTo(BigDecimal.ZERO) > 0) {
                if (i == draft.getItems().size() - 1) {
                    itemGlobalDiscount = overallDiscount.subtract(distributedDiscount);
                } else {
                    itemGlobalDiscount = it.getNetLineTotal()
                            .divide(grossTotal, 10, RoundingMode.HALF_UP)
                            .multiply(overallDiscount);
                    distributedDiscount = distributedDiscount.add(itemGlobalDiscount);
                }
            }

            BigDecimal finalTaxableAmount = it.getNetLineTotal().subtract(itemGlobalDiscount).max(BigDecimal.ZERO);
            BigDecimal effectiveUnitPrice = it.getQuantity() > 0
                    ? finalTaxableAmount.divide(BigDecimal.valueOf(it.getQuantity()), 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            txItems.add(new TaxableItem(
                    it.getVariant().productName(),
                    it.getVariant().name(),
                    effectiveUnitPrice,
                    it.getQuantity(),
                    null, // Tax category logic might need to be passed in or looked up
                    it.getVariant().id(),
                    it.getVariant().productId()
            ));
        }

        Customer customer = draft.getSelectedCustomer();
        if (customer == null && (!draft.getCustomerName().isEmpty() || !draft.getCustomerAddress().isEmpty())) {
            customer = new Customer(null, draft.getCustomerName(), draft.getCustomerPhone(), 
                                   draft.getCustomerEmail(), draft.getCustomerAddress(), 
                                   null, null, null, null, null);
        }

        TaxCalculationResult tR = taxCalculator.calculate(new TaxableInvoice(txItems), customer);

        // Update items with tax info
        for (int i = 0; i < draft.getItems().size(); i++) {
            CartItem it = draft.getItems().get(i);
            TaxableItem calculated = tR.getItemByIndex(i);
            it.setTaxAmount(calculated.getTaxAmount());
            it.setTaxRate(calculated.getTaxRate());
            it.setTaxRuleSnapshot(calculated.getTaxRuleSnapshot());
        }

        draft.setSubtotal(grossTotal);
        draft.setTaxAmount(tR.totalTax());
        draft.setTotal(tR.grandTotal());
    }
}
