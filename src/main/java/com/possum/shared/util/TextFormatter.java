package com.possum.shared.util;

/**
 * Utility class for common text formatting operations.
 * Eliminates duplicate formatting methods across controllers.
 */
public final class TextFormatter {

    private TextFormatter() {
        // Utility class
    }

    /**
     * Convert string to title case.
     * Examples: "hello_world" -> "Hello World", "ACTIVE" -> "Active"
     */
    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // Handle underscore-separated words
        String[] words = input.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1))
              .append(" ");
        }
        
        return sb.toString().trim();
    }

    /**
     * Format status string for display.
     * Examples: "active" -> "Active", "INACTIVE" -> "Inactive"
     */
    public static String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        return status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
    }

    /**
     * Extract initials from a name.
     * Examples: "John Doe" -> "JD", "Alice" -> "A", "" -> "?"
     */
    public static String initials(String name) {
        return initials(name, "?");
    }

    /**
     * Extract initials from a name with custom fallback.
     * Examples: "John Doe" -> "JD", "Alice" -> "A", "" -> fallback
     */
    public static String initials(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    /**
     * Capitalize first letter of string.
     * Examples: "hello" -> "Hello", "WORLD" -> "WORLD"
     */
    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Truncate string to max length with ellipsis.
     * Examples: truncate("Hello World", 8) -> "Hello..."
     */
    public static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength - 3) + "...";
    }

    /**
     * Convert camelCase or PascalCase to space-separated words.
     * Examples: "firstName" -> "First Name", "ProductID" -> "Product ID"
     */
    public static String camelCaseToWords(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String spaced = input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                             .replaceAll("([a-z])([A-Z])", "$1 $2")
                             .replaceAll("([a-zA-Z])([0-9])", "$1 $2")
                             .replaceAll("([0-9])([a-zA-Z])", "$1 $2");
        return capitalize(spaced);
    }

    /**
     * Format a number with thousand separators.
     * Examples: 1000 -> "1,000", 1234567 -> "1,234,567"
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Format a decimal number with thousand separators.
     * Examples: 1000.50 -> "1,000.50"
     */
    public static String formatDecimal(double number, int decimalPlaces) {
        return String.format("%,." + decimalPlaces + "f", number);
    }
}
