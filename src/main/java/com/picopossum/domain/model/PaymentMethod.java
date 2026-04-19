package com.picopossum.domain.model;

public record PaymentMethod(
        Long id,
        String name,
        String code,
        Boolean active
) {
}
