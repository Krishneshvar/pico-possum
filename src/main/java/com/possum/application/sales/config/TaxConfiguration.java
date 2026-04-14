package com.possum.application.sales.config;

import java.math.RoundingMode;

public class TaxConfiguration {
    private final TaxRoundingStrategy roundingStrategy;
    private final RoundingMode roundingMode;
    private final boolean validateNegativeRates;
    private final boolean validateNegativePrices;

    public TaxConfiguration(
            TaxRoundingStrategy roundingStrategy,
            RoundingMode roundingMode,
            boolean validateNegativeRates,
            boolean validateNegativePrices
    ) {
        this.roundingStrategy = roundingStrategy;
        this.roundingMode = roundingMode;
        this.validateNegativeRates = validateNegativeRates;
        this.validateNegativePrices = validateNegativePrices;
    }

    public static TaxConfiguration defaultConfig() {
        return new TaxConfiguration(
                TaxRoundingStrategy.INVOICE_LEVEL,
                RoundingMode.HALF_UP,
                true,
                true
        );
    }

    public TaxRoundingStrategy getRoundingStrategy() {
        return roundingStrategy;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public boolean isValidateNegativeRates() {
        return validateNegativeRates;
    }

    public boolean isValidateNegativePrices() {
        return validateNegativePrices;
    }
}
