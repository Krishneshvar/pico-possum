package com.possum.domain.enums;

public enum TransactionType {
    PAYMENT("payment"),
    REFUND("refund"),
    PURCHASE("purchase"),
    PURCHASE_REFUND("purchase_refund");

    private final String dbValue;

    TransactionType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
