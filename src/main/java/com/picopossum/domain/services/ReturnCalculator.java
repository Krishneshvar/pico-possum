package com.picopossum.domain.services;

import com.picopossum.application.returns.dto.CreateReturnItemRequest;
import com.picopossum.application.returns.dto.RefundCalculation;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.SaleItem;

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
        BigDecimal billItemsTotalWithTax = BigDecimal.ZERO;
        for (SaleItem si : saleItems) {
            BigDecimal lineSubtotal = si.pricePerUnit()
                    .multiply(BigDecimal.valueOf(si.quantity()))
                    .subtract(si.discountAmount());
            BigDecimal lineTax = si.taxAmount() != null ? si.taxAmount() : BigDecimal.ZERO;
            billItemsTotalWithTax = billItemsTotalWithTax.add(lineSubtotal).add(lineTax);
        }

        BigDecimal globalDiscount = saleGlobalDiscount != null ? saleGlobalDiscount : BigDecimal.ZERO;
        List<RefundCalculation> results = new ArrayList<>();

        for (CreateReturnItemRequest returnItem : returnItems) {
            SaleItem saleItem = saleItems.stream()
                    .filter(si -> si.id().equals(returnItem.saleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Sale item " + returnItem.saleItemId() + " not found"));

            BigDecimal linePricePerUnit = saleItem.pricePerUnit();
            BigDecimal lineQuantity = BigDecimal.valueOf(saleItem.quantity());
            BigDecimal lineDiscountAmount = saleItem.discountAmount();
            BigDecimal lineTaxAmount = saleItem.taxAmount() != null ? saleItem.taxAmount() : BigDecimal.ZERO;
            BigDecimal lineSubtotalWithTax = linePricePerUnit.multiply(lineQuantity).subtract(lineDiscountAmount).add(lineTaxAmount);

            BigDecimal lineGlobalDiscount = BigDecimal.ZERO;
            if (billItemsTotalWithTax.compareTo(BigDecimal.ZERO) > 0) {
                lineGlobalDiscount = lineSubtotalWithTax
                        .divide(billItemsTotalWithTax, 10, RoundingMode.HALF_UP)
                        .multiply(globalDiscount);
            }

            BigDecimal lineNetPaid = lineSubtotalWithTax.subtract(lineGlobalDiscount);

            BigDecimal refundAmount = lineNetPaid
                    .divide(lineQuantity, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(returnItem.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            results.add(new RefundCalculation(
                    returnItem.saleItemId(),
                    returnItem.quantity(),
                    refundAmount,
                    saleItem.productId(),
                    saleItem.pricePerUnit(),
                    saleItem.sku(),
                    saleItem.productName()
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
