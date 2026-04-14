package com.possum.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TextFormatterTest {

    @ParameterizedTest
    @CsvSource({
        "hello_world, Hello World",
        "ACTIVE, Active",
        "pending_shipment, Pending Shipment",
        "lowercase, Lowercase",
        "'', ''",
        ", ''"
    })
    @DisplayName("Should convert strings to Title Case properly")
    void toTitleCase_success(String input, String expected) {
        assertEquals(expected, TextFormatter.toTitleCase(input));
    }

    @ParameterizedTest
    @CsvSource({
        "active, Active",
        "INACTIVE, Inactive",
        "PENDING, Pending",
        "VOIDED, Voided",
        "unknown, Unknown",
        "'', Unknown",
        ", Unknown"
    })
    @DisplayName("Should format status strings properly")
    void formatStatus_success(String input, String expected) {
        assertEquals(expected, TextFormatter.formatStatus(input));
    }

    @ParameterizedTest
    @CsvSource({
        "John Doe, JD",
        "Alice, A",
        "Bob Smith, BS",
        "'', '?'",
        ", '?'"
    })
    @DisplayName("Should extract initials properly")
    void initials_success(String input, String expected) {
        assertEquals(expected, TextFormatter.initials(input));
    }

    @Test
    @DisplayName("Should capitalize string first letter")
    void capitalize_success() {
        assertEquals("Hello", TextFormatter.capitalize("hello"));
        assertEquals("WORLD", TextFormatter.capitalize("WORLD")); // Stays all-caps
        assertEquals("", TextFormatter.capitalize(""));
        assertNull(TextFormatter.capitalize(null));
    }

    @Test
    @DisplayName("Should truncate strings with ellipsis")
    void truncate_success() {
        assertEquals("Hello...", TextFormatter.truncate("Hello World", 8));
        assertEquals("Hello World", TextFormatter.truncate("Hello World", 11));
        assertEquals("ABC", TextFormatter.truncate("ABC", 5));
        assertNull(TextFormatter.truncate(null, 5));
    }

    @ParameterizedTest
    @CsvSource({
        "firstName, First Name",
        "ProductID, Product ID",
        "someLongFieldName, Some Long Field Name",
        "ID123, ID 123",
        "99Problems, 99 Problems",
        "StatusB, Status B"
    })
    @DisplayName("Should convert camel/PascalCase to space-separated words")
    void camelCaseToWords_success(String input, String expected) {
        assertEquals(expected, TextFormatter.camelCaseToWords(input));
    }

    @Test
    @DisplayName("Should format numbers with commas")
    void formatNumber_success() {
        assertEquals("1,000", TextFormatter.formatNumber(1000));
        assertEquals("1,234,567", TextFormatter.formatNumber(1234567));
        assertEquals("0", TextFormatter.formatNumber(0));
    }

    @Test
    @DisplayName("Should format decimal numbers properly")
    void formatDecimal_success() {
        assertEquals("1,000.50", TextFormatter.formatDecimal(1000.5, 2));
        assertEquals("12,345.679", TextFormatter.formatDecimal(12345.6789, 3));
        assertEquals("1,000", TextFormatter.formatDecimal(1000.49, 0));
    }
}
