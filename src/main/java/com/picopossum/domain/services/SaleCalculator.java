package com.picopossum.domain.services;

import com.picopossum.domain.model.CartItem;
import com.picopossum.domain.model.SaleDraft;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SaleCalculator implements DomainService {

    public SaleCalculator() {
    }

    public void recalculate(SaleDraft draft) {
        if (draft.getItems().isEmpty()) {
            draft.setSubtotal(BigDecimal.ZERO);
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
            mrpTotal = mrpTotal.add(it.getProduct().mrp().multiply(BigDecimal.valueOf(it.getQuantity())));
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

        draft.setSubtotal(grossTotal);
        draft.setTotal(grossTotal.subtract(overallDiscount).max(BigDecimal.ZERO));
    }
}
