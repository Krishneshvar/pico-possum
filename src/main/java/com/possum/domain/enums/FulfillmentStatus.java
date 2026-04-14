package com.possum.domain.enums;

public enum FulfillmentStatus {
    PENDING("pending"),
    FULFILLED("fulfilled"),
    CANCELLED("cancelled");

    private final String dbValue;

    FulfillmentStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
