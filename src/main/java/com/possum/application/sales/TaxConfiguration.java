package com.possum.application.sales;

public class TaxConfiguration {
    
    public enum RoundingMode {
        INVOICE_LEVEL,
        ITEM_LEVEL
    }
    
    private final RoundingMode roundingMode;
    private final boolean enableAuditTrail;
    
    public TaxConfiguration(RoundingMode roundingMode, boolean enableAuditTrail) {
        this.roundingMode = roundingMode;
        this.enableAuditTrail = enableAuditTrail;
    }
    
    public static TaxConfiguration defaultConfig() {
        return new TaxConfiguration(RoundingMode.INVOICE_LEVEL, true);
    }
    
    public RoundingMode roundingMode() {
        return roundingMode;
    }
    
    public boolean enableAuditTrail() {
        return enableAuditTrail;
    }
}
