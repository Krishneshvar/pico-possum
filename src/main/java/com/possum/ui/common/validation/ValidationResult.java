package com.possum.ui.common.validation;

/**
 * Represents the result of a validation operation.
 */
public class ValidationResult {
    
    private final boolean valid;
    private final String errorMessage;

    private ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    /**
     * Create a failed validation result with error message.
     */
    public static ValidationResult error(String message) {
        return new ValidationResult(false, message);
    }

    /**
     * Check if validation passed.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Check if validation failed.
     */
    public boolean isInvalid() {
        return !valid;
    }

    /**
     * Get error message (null if valid).
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return valid ? "Valid" : "Invalid: " + errorMessage;
    }
}
