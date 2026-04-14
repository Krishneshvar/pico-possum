package com.possum.domain.enums;

public enum SaleStatus {
    DRAFT("draft"),
    PAID("paid"),
    PARTIALLY_PAID("partially_paid"),
    CANCELLED("cancelled"),
    REFUNDED("refunded");

    private final String dbValue;

    SaleStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
