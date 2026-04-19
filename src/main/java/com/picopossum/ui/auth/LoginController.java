package com.picopossum.ui.auth;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthService;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.people.UserService;
import com.picopossum.ui.common.controls.NotificationService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

public class LoginController {

    @FXML private VBox loginForm;
    @FXML private VBox setupForm;
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;

    @FXML private TextField setupNameField;
    @FXML private TextField setupUsernameField;
    @FXML private PasswordField setupPasswordField;
    @FXML private PasswordField setupConfirmPasswordField;
    @FXML private Button setupButton;

    @FXML private Label titleLabel;

    private final AuthService authService;
    private final UserService userService;
    private final Runnable onSuccess;

    public LoginController(AuthService authService, UserService userService, Runnable onSuccess) {
        this.authService = authService;
        this.userService = userService;
        this.onSuccess = onSuccess;
    }

    @FXML
    public void initialize() {
        if (authService.anyUserExists()) {
            showLoginForm();
        } else {
            showSetupForm();
        }

        loginButton.setOnAction(e -> handleLogin());
        setupButton.setOnAction(e -> handleSetup());
        
        // Add Enter key support
        passwordField.setOnAction(e -> handleLogin());
        setupConfirmPasswordField.setOnAction(e -> handleSetup());
    }

    private void showLoginForm() {
        loginForm.setVisible(true);
        loginForm.setManaged(true);
        setupForm.setVisible(false);
        setupForm.setManaged(false);
        titleLabel.setText("Login to Pico Possum");
    }

    private void showSetupForm() {
        loginForm.setVisible(false);
        loginForm.setManaged(false);
        setupForm.setVisible(true);
        setupForm.setManaged(true);
        titleLabel.setText("First Time Setup");
    }

    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            NotificationService.error("Please enter both username and password");
            return;
        }

        Optional<AuthUser> user = authService.login(username, password);
        if (user.isPresent()) {
            AuthContext.setCurrentUser(user.get());
            onSuccess.run();
        } else {
            NotificationService.error("Invalid username or password");
        }
    }

    private void handleSetup() {
        String name = setupNameField.getText();
        String username = setupUsernameField.getText();
        String password = setupPasswordField.getText();
        String confirm = setupConfirmPasswordField.getText();

        if (name.isBlank() || username.isBlank() || password.isBlank()) {
            NotificationService.error("All fields are required");
            return;
        }

        if (!password.equals(confirm)) {
            NotificationService.error("Passwords do not match");
            return;
        }

        try {
            // Create the first user
            userService.createUser(name, username, password, true); 
            NotificationService.success("Account created successfully!");
            
            // Auto-login after setup
            Optional<AuthUser> user = authService.login(username, password);
            if (user.isPresent()) {
                AuthContext.setCurrentUser(user.get());
                onSuccess.run();
            }
        } catch (Exception e) {
            NotificationService.error(e.getMessage());
        }
    }
}
