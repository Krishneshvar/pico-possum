package com.possum.ui.common.validation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Collection of common validators.
 * Provides pre-built validators for common validation scenarios.
 */
public final class Validators {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[(]?[0-9]{1,4}[)]?[-\\s.]?[(]?[0-9]{1,4}[)]?[-\\s.]?[0-9]{1,9}$"
    );

    private Validators() {
        // Utility class
    }

    /**
     * Validator that checks if a string is not null and not empty.
     */
    public static Validator<String> required(String fieldName) {
        return value -> {
            if (value == null || value.trim().isEmpty()) {
                return ValidationResult.error(fieldName + " is required");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks minimum length.
     */
    public static Validator<String> minLength(int min, String fieldName) {
        return value -> {
            if (value != null && value.length() < min) {
                return ValidationResult.error(fieldName + " must be at least " + min + " characters");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks maximum length.
     */
    public static Validator<String> maxLength(int max, String fieldName) {
        return value -> {
            if (value != null && value.length() > max) {
                return ValidationResult.error(fieldName + " must be at most " + max + " characters");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if string matches a pattern.
     */
    public static Validator<String> pattern(Pattern pattern, String errorMessage) {
        return value -> {
            if (value != null && !value.isEmpty() && !pattern.matcher(value).matches()) {
                return ValidationResult.error(errorMessage);
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if string matches a regex pattern.
     */
    public static Validator<String> pattern(String regex, String errorMessage) {
        return pattern(Pattern.compile(regex), errorMessage);
    }

    /**
     * Validator that checks if string is a valid email.
     */
    public static Validator<String> email() {
        return pattern(EMAIL_PATTERN, "Invalid email address");
    }

    /**
     * Validator that checks if string is a valid phone number.
     */
    public static Validator<String> phone() {
        return pattern(PHONE_PATTERN, "Invalid phone number");
    }

    /**
     * Validator that checks if string contains no spaces.
     */
    public static Validator<String> noSpaces(String fieldName) {
        return value -> {
            if (value != null && value.contains(" ")) {
                return ValidationResult.error(fieldName + " cannot contain spaces");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if number is within range.
     */
    public static Validator<Integer> range(int min, int max, String fieldName) {
        return value -> {
            if (value != null && (value < min || value > max)) {
                return ValidationResult.error(fieldName + " must be between " + min + " and " + max);
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if number is positive.
     */
    public static Validator<Integer> positive(String fieldName) {
        return value -> {
            if (value != null && value <= 0) {
                return ValidationResult.error(fieldName + " must be positive");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if number is non-negative.
     */
    public static Validator<Integer> nonNegative(String fieldName) {
        return value -> {
            if (value != null && value < 0) {
                return ValidationResult.error(fieldName + " cannot be negative");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if BigDecimal is positive.
     */
    public static Validator<BigDecimal> positiveDecimal(String fieldName) {
        return value -> {
            if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResult.error(fieldName + " must be positive");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if BigDecimal is non-negative.
     */
    public static Validator<BigDecimal> nonNegativeDecimal(String fieldName) {
        return value -> {
            if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
                return ValidationResult.error(fieldName + " cannot be negative");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Validator that checks if value is not null.
     */
    public static <T> Validator<T> notNull(String fieldName) {
        return value -> {
            if (value == null) {
                return ValidationResult.error(fieldName + " is required");
            }
            return ValidationResult.success();
        };
    }

    /**
     * Custom validator with predicate and message.
     */
    public static <T> Validator<T> custom(java.util.function.Predicate<T> predicate, String errorMessage) {
        return value -> {
            if (!predicate.test(value)) {
                return ValidationResult.error(errorMessage);
            }
            return ValidationResult.success();
        };
    }
}
