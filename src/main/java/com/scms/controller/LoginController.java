package com.scms.controller;

import com.scms.model.User;
import com.scms.service.AuthService;
import com.scms.service.ServiceException;
import com.scms.util.RoleManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        if (messageLabel != null) messageLabel.setText("");
    }

    @FXML
    protected void onLogin(ActionEvent event) {
        String user = usernameField == null ? "" : usernameField.getText().trim();
        String pass = passwordField == null ? "" : passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            if (messageLabel != null) messageLabel.setText("Please enter username & password.");
            return;
        }

        try {
            User auth = AuthService.authenticate(user, pass);
            if (auth != null) {
                // open main window
                RoleManager.setLoggedInUser(auth);
                try {
                    Parent root = FXMLLoader.load(getClass().getResource("/com/scms/view/main.fxml"));
                    Stage stage = (Stage) loginButton.getScene().getWindow();
                    stage.setScene(new Scene(root));
                    stage.setTitle("SCMS - Dashboard (" + auth.getUsername() + ")");
                } catch (Exception e) {
                    e.printStackTrace();
                    if (messageLabel != null) messageLabel.setText("Failed to open dashboard: " + e.getMessage());
                }
            }
        } catch (ServiceException se) {
            // Provide additional detail if there is a SQLException cause to help diagnose local DB issues
            String msg = se.getMessage();
            if (("auth.error").equals(msg) && se.getCause() != null) {
                if (messageLabel != null) messageLabel.setText("Login failed (DB): " + se.getCause().getMessage());
            } else if (("auth.invalid_credentials").equals(msg)) {
                if (messageLabel != null) messageLabel.setText("Invalid username or password.");
            } else {
                if (messageLabel != null) messageLabel.setText("Login failed: " + msg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            if (messageLabel != null) messageLabel.setText("Unexpected error: " + ex.getMessage());
        }
    }
}
