package com.picopossum.domain.model;

public enum DiscountType {
    FIXED,
    PERCENTAGE;

    public static DiscountType fromString(String value) {
        if (value == null) return FIXED;
        return switch (value.toLowerCase()) {
            case "pct", "percentage" -> PERCENTAGE;
            default -> FIXED;
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
