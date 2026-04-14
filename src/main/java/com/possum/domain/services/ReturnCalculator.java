package com.possum.domain.services;

import com.possum.application.returns.dto.CreateReturnItemRequest;
import com.possum.application.returns.dto.RefundCalculation;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.SaleItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class ReturnCalculator {

    public List<RefundCalculation> calculateRefunds(
            List<CreateReturnItemRequest> returnItems,
            List<SaleItem> saleItems,
            BigDecimal saleGlobalDiscount
    ) {
        // Calculate bill items subtotal (before global discount)
        BigDecimal billItemsSubtotal = BigDecimal.ZERO;
        for (SaleItem si : saleItems) {
            BigDecimal lineSubtotal = si.pricePerUnit()
                    .multiply(BigDecimal.valueOf(si.quantity()))
                    .subtract(si.discountAmount());
            billItemsSubtotal = billItemsSubtotal.add(lineSubtotal);
        }

        BigDecimal globalDiscount = saleGlobalDiscount != null ? saleGlobalDiscount : BigDecimal.ZERO;
        List<RefundCalculation> results = new ArrayList<>();

        for (CreateReturnItemRequest returnItem : returnItems) {
            SaleItem saleItem = saleItems.stream()
                    .filter(si -> si.id().equals(returnItem.saleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Sale item " + returnItem.saleItemId() + " not found"));

            // 1. Line subtotal (after line-level discount)
            BigDecimal linePricePerUnit = saleItem.pricePerUnit();
            BigDecimal lineQuantity = BigDecimal.valueOf(saleItem.quantity());
            BigDecimal lineDiscountAmount = saleItem.discountAmount();
            BigDecimal lineSubtotal = linePricePerUnit.multiply(lineQuantity).subtract(lineDiscountAmount);

            // 2. Pro-rated global discount for this line
            BigDecimal lineGlobalDiscount = BigDecimal.ZERO;
            if (billItemsSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                lineGlobalDiscount = lineSubtotal
                        .divide(billItemsSubtotal, 10, RoundingMode.HALF_UP)
                        .multiply(globalDiscount);
            }

            // 3. Line net paid (after both discounts)
            BigDecimal lineNetPaid = lineSubtotal.subtract(lineGlobalDiscount);

            // 4. Refund for the returned quantity
            BigDecimal refundAmount = lineNetPaid
                    .divide(lineQuantity, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(returnItem.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            results.add(new RefundCalculation(
                    returnItem.saleItemId(),
                    returnItem.quantity(),
                    refundAmount,
                    saleItem.variantId()
            ));
        }

        return results;
    }

    public BigDecimal calculateTotalRefund(List<RefundCalculation> refundItems) {
        return refundItems.stream()
                .map(RefundCalculation::refundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
