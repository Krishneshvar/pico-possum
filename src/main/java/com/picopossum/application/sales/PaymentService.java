package com.picopossum.application.sales;

import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.model.PaymentMethod;
import com.picopossum.domain.repositories.SalesRepository;

import java.util.List;

public class PaymentService {
    private final SalesRepository salesRepository;

    public PaymentService(SalesRepository salesRepository) {
        this.salesRepository = salesRepository;
    }

    public void validatePaymentMethod(long paymentMethodId) {
        if (!salesRepository.paymentMethodExists(paymentMethodId)) {
            throw new NotFoundException("Payment method not found: " + paymentMethodId);
        }
    }

    public List<PaymentMethod> getActivePaymentMethods() {
        return salesRepository.findPaymentMethods();
    }
}
