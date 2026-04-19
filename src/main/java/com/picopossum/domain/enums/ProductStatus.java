package com.picopossum.domain.enums;

public enum ProductStatus {
    ACTIVE("active"),
    INACTIVE("inactive"),
    DISCONTINUED("discontinued");

    private final String dbValue;

    ProductStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
