package com.possum.domain.enums;

public enum InventoryReason {
    SALE("sale"),
    RETURN("return"),
    CONFIRM_RECEIVE("confirm_receive"),
    SPOILAGE("spoilage"),
    DAMAGE("damage"),
    THEFT("theft"),
    CORRECTION("correction");

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
