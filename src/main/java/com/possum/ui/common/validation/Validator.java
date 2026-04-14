package com.possum.ui.common.validation;

/**
 * Interface for validation rules.
 * Implementations validate a value and return a result.
 */
@FunctionalInterface
public interface Validator<T> {
    
    /**
     * Validate a value.
     * 
     * @param value The value to validate
     * @return ValidationResult indicating success or failure with message
     */
    ValidationResult validate(T value);

    /**
     * Chain this validator with another validator.
     * Both validators must pass for the result to be valid.
     */
    default Validator<T> and(Validator<T> other) {
        return value -> {
            ValidationResult first = this.validate(value);
            if (first.isInvalid()) {
                return first;
            }
            return other.validate(value);
        };
    }

    /**
     * Chain this validator with another validator using OR logic.
     * At least one validator must pass for the result to be valid.
     */
    default Validator<T> or(Validator<T> other) {
        return value -> {
            ValidationResult first = this.validate(value);
            if (first.isValid()) {
                return first;
            }
            return other.validate(value);
        };
    }
}
