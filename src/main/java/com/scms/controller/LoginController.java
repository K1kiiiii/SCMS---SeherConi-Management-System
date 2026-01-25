package com.scms.controller;

import com.scms.model.User;
import com.scms.service.AuthService;
import com.scms.service.ServiceException;
import com.scms.util.RoleManager;
import com.scms.util.LoadingOverlay;
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


        javafx.application.Platform.runLater(() -> {
            Scene s = loginButton == null ? null : loginButton.getScene();
            if (s != null) {
                boolean dark = prefs.getBoolean(PREF_DARK, false); // default to LIGHT per design
                applyThemeToScene(s, dark);
                updateToggleIcon(dark);

                if (darkModeToggle != null && darkModeToggle.getParent() != null) {
                    darkModeToggle.toFront();
                }

                if (darkModeToggle != null) {
                    darkModeToggle.setStyle("-fx-text-fill: #4A3428;");
                }

                s.windowProperty().addListener((obsW, oldW, newW) -> {
                    boolean curDark = prefs.getBoolean(PREF_DARK, false);
                    applyThemeToScene(s, curDark);
                });
            } else {
                if (loginButton != null) {
                    loginButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
                        if (newScene != null) {
                            boolean dark = prefs.getBoolean(PREF_DARK, false);
                            applyThemeToScene(newScene, dark);
                            updateToggleIcon(dark);
                            if (darkModeToggle != null) darkModeToggle.setStyle("-fx-text-fill: #4A3428;");
                        }
                    });
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
        applyThemeToScene(s, isDark);
        updateToggleIcon(isDark);

        if (darkModeToggle != null) {
            darkModeToggle.getStyleClass().removeAll("dark", "light");
            darkModeToggle.getStyleClass().add(isDark ? "dark" : "light");
        }
    }

    private void updateToggleIcon(boolean dark) {
        if (darkModeToggle == null) return;
        darkModeToggle.setText(dark ? "☀️" : "🌙");
        darkModeToggle.getStyleClass().removeAll("dark", "light");
        darkModeToggle.getStyleClass().add(dark ? "dark" : "light");
    }

    private void applyThemeToScene(Scene scene, boolean dark) {
        try {
            java.net.URL darkUrl = getClass().getResource(DARK_CSS);
            java.net.URL lightUrl = getClass().getResource(LIGHT_CSS);
            String darkCss = darkUrl == null ? null : darkUrl.toExternalForm();
            String lightCss = lightUrl == null ? null : lightUrl.toExternalForm();

            scene.getStylesheets().clear();

            if (dark) {
                if (darkCss != null) scene.getStylesheets().add(darkCss);
                System.out.println("[DEBUG] LoginController: applied dark css=" + darkCss + " prefs.dark=" + prefs.getBoolean(PREF_DARK, false));
                // set fallback scene fill and root inline style
                scene.setFill(javafx.scene.paint.Color.web("#1F1F1F"));
                try { if (scene.getRoot() != null) scene.getRoot().setStyle("-fx-background-color: #1F1F1F;"); } catch (Exception ignore) {}
            } else {
                if (lightCss != null) scene.getStylesheets().add(lightCss);
                System.out.println("[DEBUG] LoginController: applied light css=" + lightCss + " prefs.dark=" + prefs.getBoolean(PREF_DARK, false));
                scene.setFill(javafx.scene.paint.Color.web("#F5F3EF"));
                try { if (scene.getRoot() != null) scene.getRoot().setStyle("-fx-background-color: #F5F3EF;"); } catch (Exception ignore) {}
            }
        } catch (Exception ex) {
            System.err.println("Failed to apply theme: " + ex.getMessage());
        }
    }

    private void setDarkMode(Scene scene, boolean dark) {
        applyThemeToScene(scene, dark);
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

                    // show loading overlay while preparing main scene
                    LoadingOverlay.show(loginButton);

                    Scene mainScene = new Scene(root);
                    boolean darkPref = prefs.getBoolean(PREF_DARK, false);
                    setDarkMode(mainScene, darkPref);
                    stage.setScene(mainScene);
                    stage.setTitle("SCMS - Dashboard (" + auth.getUsername() + ")");
                    stage.setMaximized(true);

                    // hide overlay after short delay to ensure scene fully rendered
                    javafx.application.Platform.runLater(() -> LoadingOverlay.hide(loginButton));
                 } catch (Exception e) {
                    System.err.println("Failed to open dashboard: " + e.getMessage());
                    if (messageLabel != null) messageLabel.setText("Failed to open dashboard: " + e.getMessage());
                }
            }
        } catch (ServiceException se) {
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
