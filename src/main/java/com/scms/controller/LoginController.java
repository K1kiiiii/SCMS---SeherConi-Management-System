package com.scms.controller;

import com.scms.model.User;
import com.scms.service.AuthService;
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
        messageLabel.setText("");
    }

    @FXML
    protected void onLogin(ActionEvent event) {
        String user = usernameField.getText().trim();
        String pass = passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            messageLabel.setText("Please enter username & password.");
            return;
        }

        User auth = AuthService.authenticate(user, pass);
        if (auth != null) {
            // open main window
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/com/scms/view/main.fxml"));
                Stage stage = (Stage) loginButton.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("SCMS - Dashboard (" + auth.getUsername() + ")");
            } catch (Exception e) {
                e.printStackTrace();
                messageLabel.setText("Failed to open dashboard: " + e.getMessage());
            }
        } else {
            messageLabel.setText("Invalid credentials.");
        }
    }
}
