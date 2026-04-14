package com.possum.ui.common.controls;

import javafx.scene.control.TextFormatter;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Common input filters for JavaFX controls to prevent invalid data entry.
 */
public final class InputFilters {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("\\d*(\\.\\d*)?");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d*");

    private InputFilters() {}

    /**
     * Allows only decimal numbers (non-negative).
     */
    public static TextFormatter<String> decimalFormat() {
        return new TextFormatter<>(createFilter(DECIMAL_PATTERN));
    }

    /**
     * Allows only whole numbers (non-negative).
     */
    public static TextFormatter<String> numericFormat() {
        return new TextFormatter<>(createFilter(NUMERIC_PATTERN));
    }

    private static UnaryOperator<TextFormatter.Change> createFilter(Pattern pattern) {
        return change -> {
            String newText = change.getControlNewText();
            if (pattern.matcher(newText).matches()) {
                return change;
            }
            return null;
        };
    }
}
