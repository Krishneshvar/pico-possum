package com.possum.ui.auth;

import com.possum.infrastructure.security.PasswordStrengthCalculator;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;

public class PasswordStrengthIndicator extends HBox {
    
    private final ProgressBar strengthBar;
    private final Label strengthLabel;
    
    public PasswordStrengthIndicator() {
        setSpacing(10);
        
        strengthBar = new ProgressBar(0);
        strengthBar.setPrefWidth(150);
        
        strengthLabel = new Label("No password");
        strengthLabel.getStyleClass().add("strength-label");
        
        getChildren().addAll(strengthBar, strengthLabel);
    }
    
    public void updateStrength(String password) {
        if (password == null || password.isEmpty()) {
            strengthBar.setProgress(0);
            strengthLabel.setText("No password");
            strengthLabel.getStyleClass().setAll("strength-label");
            return;
        }
        
        var result = PasswordStrengthCalculator.calculateStrength(password);
        strengthBar.setProgress(result.score() / 100.0);
        strengthLabel.setText(result.level().toString());
        
        strengthLabel.getStyleClass().setAll("strength-label", "strength-" + result.level().toString().toLowerCase());
    }
}
