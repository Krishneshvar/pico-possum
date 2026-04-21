package com.picopossum.application.sales;

import com.picopossum.domain.repositories.SalesRepository;
import java.time.LocalDate;

public class InvoiceNumberService {
    private final SalesRepository salesRepository;

    public InvoiceNumberService(SalesRepository salesRepository) {
        this.salesRepository = salesRepository;
    }

    public String generate(String typePrefix, Long primaryPaymentMethodId) {
        String code = "CH"; // Default to Cash if not provided
        if (primaryPaymentMethodId != null && primaryPaymentMethodId > 0) {
            code = salesRepository.getPaymentMethodCode(primaryPaymentMethodId)
                    .filter(c -> c != null && !c.isBlank())
                    .orElse("CH");
        }

        LocalDate today = LocalDate.now();
        String yy = String.format("%02d", today.getYear() % 100);

        // Sequence is shared across payment methods per year.
        long seq = salesRepository.getNextSequenceForPaymentType(typePrefix + "_GLOBAL_" + today.getYear());

        return String.format("%s%s%s%07d", typePrefix, yy, code, seq);
    }
}

