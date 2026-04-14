package com.possum.domain.enums;

public enum PurchaseStatus {
    PENDING("pending"),
    RECEIVED("received"),
    CANCELLED("cancelled");

    private final String dbValue;

    PurchaseStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
