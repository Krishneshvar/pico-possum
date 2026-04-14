package com.possum.ui.common.validation;

import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ComboBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent API for validating JavaFX form fields.
 * Automatically manages error styling and error labels.
 */
public class FieldValidator<T> {
    
    private final Control control;
    private final Label errorLabel;
    private final List<Validator<T>> validators = new ArrayList<>();
    private java.util.function.Function<Control, T> valueExtractor;

    private FieldValidator(Control control, Label errorLabel) {
        this.control = control;
        this.errorLabel = errorLabel;
    }

    /**
     * Create a field validator for a TextField.
     */
    public static FieldValidator<String> forField(TextField field, Label errorLabel) {
        FieldValidator<String> validator = new FieldValidator<>(field, errorLabel);
        validator.valueExtractor = c -> ((TextField) c).getText();
        return validator;
    }

    /**
     * Shortcut to create a field validator for a TextField without an error label.
     */
    public static FieldValidator<String> of(TextField field) {
        return forField(field, null);
    }

    /**
     * Shortcut to create a field validator for a ComboBox without an error label.
     */
    public static <T> FieldValidator<T> of(ComboBox<T> field) {
        return forField(field, null);
    }

    /**
     * Create a field validator for a TextArea.
     */
    public static FieldValidator<String> forField(TextArea field, Label errorLabel) {
        FieldValidator<String> validator = new FieldValidator<>(field, errorLabel);
        validator.valueExtractor = c -> ((TextArea) c).getText();
        return validator;
    }

    /**
     * Create a field validator for a ComboBox.
     */
    public static <T> FieldValidator<T> forField(ComboBox<T> field, Label errorLabel) {
        FieldValidator<T> validator = new FieldValidator<>(field, errorLabel);
        validator.valueExtractor = c -> ((ComboBox<T>) c).getValue();
        return validator;
    }

    /**
     * Create a field validator with custom value extractor.
     */
    public static <T> FieldValidator<T> forField(Control control, Label errorLabel, 
                                                   java.util.function.Function<Control, T> valueExtractor) {
        FieldValidator<T> validator = new FieldValidator<>(control, errorLabel);
        validator.valueExtractor = valueExtractor;
        return validator;
    }

    /**
     * Shortcut to create a field validator with custom value extractor.
     */
    public static <T> FieldValidator<T> of(Control control, java.util.function.Function<Control, T> valueExtractor) {
        return forField(control, null, valueExtractor);
    }

    /**
     * Add a validator to the chain.
     */
    public FieldValidator<T> addValidator(Validator<T> validator) {
        validators.add(validator);
        return this;
    }

    /**
     * Add required validation.
     */
    public FieldValidator<String> required(String fieldName) {
        if (valueExtractor == null) {
            throw new IllegalStateException("Value extractor not set");
        }
        @SuppressWarnings("unchecked")
        FieldValidator<String> stringValidator = (FieldValidator<String>) this;
        stringValidator.validators.add(Validators.required(fieldName));
        return stringValidator;
    }

    /**
     * Add minimum length validation.
     */
    public FieldValidator<String> minLength(int min, String fieldName) {
        @SuppressWarnings("unchecked")
        FieldValidator<String> stringValidator = (FieldValidator<String>) this;
        stringValidator.validators.add(Validators.minLength(min, fieldName));
        return stringValidator;
    }

    /**
     * Add maximum length validation.
     */
    public FieldValidator<String> maxLength(int max, String fieldName) {
        @SuppressWarnings("unchecked")
        FieldValidator<String> stringValidator = (FieldValidator<String>) this;
        stringValidator.validators.add(Validators.maxLength(max, fieldName));
        return stringValidator;
    }

    /**
     * Add email validation.
     */
    public FieldValidator<String> email() {
        @SuppressWarnings("unchecked")
        FieldValidator<String> stringValidator = (FieldValidator<String>) this;
        stringValidator.validators.add(Validators.email());
        return stringValidator;
    }

    /**
     * Add phone validation.
     */
    public FieldValidator<String> phone() {
        @SuppressWarnings("unchecked")
        FieldValidator<String> stringValidator = (FieldValidator<String>) this;
        stringValidator.validators.add(Validators.phone());
        return stringValidator;
    }

    /**
     * Add no spaces validation.
     */
    public FieldValidator<String> noSpaces(String fieldName) {
        @SuppressWarnings("unchecked")
        FieldValidator<String> stringValidator = (FieldValidator<String>) this;
        stringValidator.validators.add(Validators.noSpaces(fieldName));
        return stringValidator;
    }

    /**
     * Add not null validation.
     */
    public FieldValidator<T> notNull(String fieldName) {
        validators.add(Validators.notNull(fieldName));
        return this;
    }

    /**
     * Add custom validation.
     */
    public FieldValidator<T> custom(java.util.function.Predicate<T> predicate, String errorMessage) {
        validators.add(Validators.custom(predicate, errorMessage));
        return this;
    }

    /**
     * Validate the field and update UI accordingly.
     * 
     * @return true if valid, false otherwise
     */
    public boolean validate() {
        T value = valueExtractor.apply(control);
        
        for (Validator<T> validator : validators) {
            ValidationResult result = validator.validate(value);
            if (result.isInvalid()) {
                showError(result.getErrorMessage());
                return false;
            }
        }
        
        clearError();
        return true;
    }

    /**
     * Setup automatic validation on focus lost.
     */
    public FieldValidator<T> validateOnFocusLost() {
        control.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                validate();
            }
        });
        return this;
    }

    /**
     * Setup automatic validation whenever the value changes.
     */
    public FieldValidator<T> validateOnType() {
        if (control instanceof TextField tf) {
            tf.textProperty().addListener((obs, old, newVal) -> validate());
        } else if (control instanceof TextArea ta) {
            ta.textProperty().addListener((obs, old, newVal) -> validate());
        } else if (control instanceof ComboBox<?> cb) {
            cb.valueProperty().addListener((obs, old, newVal) -> validate());
        }
        return this;
    }

    /**
     * Get the current value.
     */
    public T getValue() {
        return valueExtractor.apply(control);
    }

    /**
     * Show error on the field.
     */
    private void showError(String message) {
        ValidationDecorator.decorate(control, message);
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    /**
     * Clear error from the field.
     */
    private void clearError() {
        ValidationDecorator.decorate(control, null);
        if (errorLabel != null) {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    /**
     * Clear error manually.
     */
    public void clear() {
        clearError();
    }
}
