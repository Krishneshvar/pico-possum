package com.possum.domain.enums;

public enum FlowEventType {
    PURCHASE("purchase"),
    SALE("sale"),
    RETURN("return"),
    ADJUSTMENT("adjustment");

    private final String value;

    FlowEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FlowEventType fromValue(String value) {
        for (FlowEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid flow event type: " + value);
    }
}
