package com.picopossum.domain.enums;

/**
 * Audit reasons for stock movements.
 * Strictly synchronized with V1__init.sql CHECK constraints.
 */
public enum InventoryReason {
    SALE("sale"),
    RETURN("return"),
    RECEIVE("receive"),
    DAMAGE("damage"),
    THEFT("theft"),
    CORRECTION("correction"),
    CLEANUP("cleanup");

    private final String value;

    InventoryReason(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static InventoryReason fromValue(String value) {
        for (InventoryReason reason : values()) {
            if (reason.value.equals(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Invalid inventory reason: " + value);
    }
}
