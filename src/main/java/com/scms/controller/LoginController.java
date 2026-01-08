package com.scms.controller;

import com.scms.model.User;
import com.scms.service.AuthService;
import com.scms.service.ServiceException;
import com.scms.util.RoleManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.prefs.Preferences;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;
    @FXML private Button darkModeToggle;

    private static final String DARK_CSS = "/com/scms/css/dark-theme.css";
    private static final String LIGHT_CSS = "/com/scms/css/light-theme.css";
    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
    private static final String PREF_DARK = "dark_mode";

    @FXML
    public void initialize() {
        if (messageLabel != null) messageLabel.setText("");

        // apply persisted dark mode preference if scene is already available
        // use Platform.runLater to ensure the scene is ready
        javafx.application.Platform.runLater(() -> {
            Scene s = loginButton == null ? null : loginButton.getScene();
            if (s != null) {
                boolean dark = prefs.getBoolean(PREF_DARK, false); // default to LIGHT per design
                setDarkMode(s, dark);
                updateToggleIcon(dark);

                // ensure dark mode toggle is on top and clickable
                if (darkModeToggle != null && darkModeToggle.getParent() != null) {
                    darkModeToggle.toFront();
                }
            }
        });
    }

    // Called from login.fxml darkModeToggle button
    @FXML
    protected void onToggleDarkMode() {
        Scene s = loginButton == null ? null : loginButton.getScene();
        if (s == null) return;
        boolean isDark = prefs.getBoolean(PREF_DARK, false);
        isDark = !isDark;
        prefs.putBoolean(PREF_DARK, isDark);
        setDarkMode(s, isDark);
        updateToggleIcon(isDark);
    }

    private void updateToggleIcon(boolean dark) {
        if (darkModeToggle == null) return;
        darkModeToggle.setText(dark ? "☀️" : "🌙");
    }

    private void setDarkMode(Scene scene, boolean dark) {
        try {
            java.net.URL darkUrl = getClass().getResource(DARK_CSS);
            java.net.URL lightUrl = getClass().getResource(LIGHT_CSS);
            String darkCss = darkUrl == null ? null : darkUrl.toExternalForm();
            String lightCss = lightUrl == null ? null : lightUrl.toExternalForm();

            // remove both first
            if (darkCss != null) scene.getStylesheets().remove(darkCss);
            if (lightCss != null) scene.getStylesheets().remove(lightCss);

            if (dark) {
                if (darkCss != null && !scene.getStylesheets().contains(darkCss)) scene.getStylesheets().add(darkCss);
            } else {
                if (lightCss != null && !scene.getStylesheets().contains(lightCss)) scene.getStylesheets().add(lightCss);
            }
        } catch (Exception ex) {
            // safe fallback: ignore styling errors
            System.err.println("Failed to apply theme: " + ex.getMessage());
        }
    }

    @FXML
    protected void onLogin() {
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
                    java.net.URL mainUrl = getClass().getResource("/com/scms/view/main.fxml");
                    if (mainUrl == null) {
                        if (messageLabel != null) messageLabel.setText("Failed to open dashboard: resource missing");
                        return;
                    }
                    Parent root = FXMLLoader.load(mainUrl);
                    Stage stage = (Stage) loginButton.getScene().getWindow();
                    stage.setScene(new Scene(root));
                    stage.setTitle("SCMS - Dashboard (" + auth.getUsername() + ")");
                } catch (Exception e) {
                    System.err.println("Failed to open dashboard: " + e.getMessage());
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
            System.err.println("Unexpected login error: " + ex.getMessage());
            if (messageLabel != null) messageLabel.setText("Unexpected error: " + ex.getMessage());
        }
    }
}
