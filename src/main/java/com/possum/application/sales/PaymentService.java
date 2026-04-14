package com.possum.application.sales;

import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.model.PaymentMethod;
import com.possum.domain.model.Transaction;
import com.possum.domain.repositories.SalesRepository;

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

    public long recordTransaction(Transaction transaction, Long saleId) {
        return salesRepository.insertTransaction(transaction, saleId);
    }
}
