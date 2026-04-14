package com.possum.ui.common.validation;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;
import javafx.scene.control.TextInputControl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A simple fluent API for real-time form validation.
 */
public class SimpleValidator {

    private final List<Rule<?>> rules = new ArrayList<>();
    private final BooleanProperty allValid = new SimpleBooleanProperty(true);

    public <T> SimpleValidator addRule(Control control, ObservableValue<T> observable, Predicate<T> predicate, String errorMessage) {
        Rule<T> rule = new Rule<>(control, observable, predicate, errorMessage);
        rules.add(rule);
        
        observable.addListener((obs, oldV, newV) -> validateRule(rule));
        return this;
    }

    public SimpleValidator addRequiredRule(TextInputControl input, String fieldName) {
        return addRule(input, input.textProperty(), 
            val -> val != null && !val.toString().trim().isEmpty(), 
            fieldName + " is required");
    }

    public boolean validateAll() {
        boolean valid = true;
        for (Rule<?> rule : rules) {
            if (!validateByRule(rule)) {
                valid = false;
            }
        }
        allValid.set(valid);
        return valid;
    }

    private <T> boolean validateRule(Rule<T> rule) {
        boolean isValid = rule.predicate.test(rule.observable.getValue());
        ValidationDecorator.decorate(rule.control, isValid ? null : rule.errorMessage);
        updateAllValid();
        return isValid;
    }

    @SuppressWarnings("unchecked")
    private boolean validateByRule(Rule<?> rule) {
        Predicate<Object> predicate = (Predicate<Object>) rule.predicate;
        boolean isValid = predicate.test(rule.observable.getValue());
        ValidationDecorator.decorate(rule.control, isValid ? null : rule.errorMessage);
        return isValid;
    }

    private void updateAllValid() {
        boolean valid = true;
        for (Rule<?> rule : rules) {
            if (!validateByRuleWithoutDecoration(rule)) {
                valid = false;
                break;
            }
        }
        allValid.set(valid);
    }

    @SuppressWarnings("unchecked")
    private boolean validateByRuleWithoutDecoration(Rule<?> rule) {
        Predicate<Object> predicate = (Predicate<Object>) rule.predicate;
        return predicate.test(rule.observable.getValue());
    }

    public BooleanProperty allValidProperty() {
        return allValid;
    }

    private record Rule<T>(
        Control control, 
        ObservableValue<T> observable, 
        Predicate<T> predicate, 
        String errorMessage
    ) {}
}
