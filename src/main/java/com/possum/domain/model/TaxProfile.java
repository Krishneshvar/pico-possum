package com.possum.domain.model;

import java.time.LocalDateTime;

public record TaxProfile(
        Long id,
        String name,
        String countryCode,
        String regionCode,
        String pricingMode,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
