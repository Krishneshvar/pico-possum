package com.possum.application.sales;

import com.possum.domain.repositories.SalesRepository;
import java.time.LocalDate;

public class InvoiceNumberService {
    private final SalesRepository salesRepository;

    public InvoiceNumberService(SalesRepository salesRepository) {
        this.salesRepository = salesRepository;
    }

    public String generate(long primaryPaymentMethodId) {
        String code = "XX";
        if (primaryPaymentMethodId > 0) {
            code = salesRepository.getPaymentMethodCode(primaryPaymentMethodId)
                    .filter(c -> c != null && !c.isBlank())
                    .orElse("XX");
        }

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        String yy = String.format("%02d", year % 100);

        // Sequence resets every year and is shared across payment methods.
        // We use a fixed prefix "S_GLOBAL_" + year to ensure year-based reset and shared sequence.
        long seq = salesRepository.getNextSequenceForPaymentType("S_GLOBAL_" + year);

        return String.format("S%s%s%07d", yy, code, seq);
    }
}

