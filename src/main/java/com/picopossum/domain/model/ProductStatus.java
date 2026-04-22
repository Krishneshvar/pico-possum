package com.picopossum.domain.model;

import com.picopossum.shared.util.TextFormatter;

public enum ProductStatus {
    ACTIVE,
    INACTIVE,
    DISCONTINUED;

    public static ProductStatus fromString(String value) {
        if (value == null) return ACTIVE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public String toDisplayString() {
        return TextFormatter.toTitleCase(name());
    }
}
