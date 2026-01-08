package com.scms.controller;

import com.scms.model.User;
import com.scms.util.RoleManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {

    @FXML private Label welcomeLabel;
    @FXML private Label userInfoLabel;
    @FXML private StackPane contentArea;
    @FXML private ImageView logoImage;
    @FXML private Label titleLabel;

    // side menu and buttons
    @FXML private VBox sideMenu;
    @FXML private Button btnDashboard;
    @FXML private Button btnWarehouse;
    @FXML private Button btnRequests;
    @FXML private Button btnRecipes;
    @FXML private Button btnUsers;
    @FXML private Button btnStatistics;
    @FXML private Button btnReports;

    @FXML
    public void initialize() {
        User u = RoleManager.getLoggedInUser();
        if (u != null) {
            String role = u.getRole() != null ? u.getRole() : "";
            userInfoLabel.setText("Prijavljen: " + u.getUsername() + " (" + role + ")");
            welcomeLabel.setText("Dobrodošli, " + u.getUsername() + "!");
        } else {
            userInfoLabel.setText("Niste prijavljeni");
            welcomeLabel.setText("Dobrodošli u SCMS");
        }
        applyRoleVisibility();

        // load default dashboard
        loadPage("/com/scms/view/dashboard.fxml");
        // highlight dashboard as active
        setActiveButton(btnDashboard);

        // Ensure the app stylesheet is applied to the main scene; the scene may not be available yet
        Platform.runLater(() -> {
            if (contentArea == null) return;
            Scene scene = contentArea.getScene();
            if (scene != null) {
                addAppStylesheet(scene);
            } else {
                // listen for when the scene becomes available
                contentArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) addAppStylesheet(newScene);
                });
            }
        });
    }

    private void addAppStylesheet(Scene scene) {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(com.scms.controller.LoginController.class);
            boolean dark = prefs.getBoolean("dark_mode", false);
            String darkPath = "/com/scms/css/dark-theme.css";
            String lightPath = "/com/scms/css/light-theme.css";
            java.net.URL darkUrl = getClass().getResource(darkPath);
            java.net.URL lightUrl = getClass().getResource(lightPath);
            String darkCss = darkUrl == null ? null : darkUrl.toExternalForm();
            String lightCss = lightUrl == null ? null : lightUrl.toExternalForm();

            // debug: print resolved css URLs
            System.out.println("[DEBUG] addAppStylesheet: darkCss=" + darkCss + " lightCss=" + lightCss + " scene.stylesheets=" + scene.getStylesheets());

            // remove both if present from scene
            // clear all to avoid inconsistent stacking
            scene.getStylesheets().clear();

            // also remove from root stylesheets as a fallback
            try {
                if (scene.getRoot() != null) {
                    scene.getRoot().getStylesheets().clear();
                }
            } catch (Exception ignore) { }

            if (dark) {
                if (darkCss != null) {
                    scene.getStylesheets().add(darkCss);
                    System.out.println("[DEBUG] added dark stylesheet: " + darkCss);
                }
                try { if (scene.getRoot() != null) scene.getRoot().setStyle("-fx-background-color: #1F1F1F;"); } catch (Exception ignore) {}
                try { scene.setFill(javafx.scene.paint.Color.web("#1F1F1F")); } catch (Exception ignore) {}
            } else {
                if (lightCss != null) {
                    scene.getStylesheets().add(lightCss);
                    System.out.println("[DEBUG] added light stylesheet: " + lightCss);
                }
                try { if (scene.getRoot() != null) scene.getRoot().setStyle("-fx-background-color: #F5F3EF;"); } catch (Exception ignore) {}
                try { scene.setFill(javafx.scene.paint.Color.web("#F5F3EF")); } catch (Exception ignore) {}
            }

            System.out.println("[DEBUG] final scene.stylesheets=" + scene.getStylesheets());
            try { System.out.println("[DEBUG] root.stylesheets=" + (scene.getRoot() == null ? "null" : scene.getRoot().getStylesheets())); } catch (Exception ignore) {}

        } catch (Exception ex) {
            System.err.println("Could not load app stylesheet: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void applyRoleVisibility() {
        // admin ima pristup svemu
        if (RoleManager.isAdmin()) {
            return;
        }

        if (RoleManager.isMagacioner()) {
            // magacioner ne upravlja korisnicima i statistikama
            btnUsers.setVisible(false);
            btnStatistics.setVisible(false);
            // magacioner should not see recipes or reports
            if (btnRecipes != null) { btnRecipes.setVisible(false); btnRecipes.setManaged(false); }
            if (btnReports != null) { btnReports.setVisible(false); btnReports.setManaged(false); }
            return;
        }

        if (RoleManager.isRadnik()) {
            // radnik ne upravlja korisnicima i statistikama
            btnUsers.setVisible(false);
            btnStatistics.setVisible(false);
            // radnik nema pristup izvještajima i receptima
            if (btnReports != null) { btnReports.setVisible(false); btnReports.setManaged(false); }
            if (btnRecipes != null) { btnRecipes.setVisible(false); btnRecipes.setManaged(false); }
            return;
        }
    }

    @FXML
    private void handleLogout() {
        try {
            RoleManager.setLoggedInUser(null);

            java.net.URL loginUrl = getClass().getResource("/com/scms/view/login.fxml");
            if (loginUrl == null) {
                System.err.println("Login FXML resource missing");
                return;
            }

            Parent loginView = FXMLLoader.load(loginUrl);
            javafx.scene.Scene scene =  new javafx.scene.Scene(loginView);
            // apply user-preferred app stylesheet
            addAppStylesheet(scene);
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            System.err.println("Failed to open login view: " + e.getMessage());
        }
    }

    @FXML
    private void handleDashboard() {
        setActiveButton(btnDashboard);
        loadPage("/com/scms/view/dashboard.fxml");
    }

    @FXML
    private void handleViewItems() {
        setActiveButton(btnWarehouse);
        loadPage("/com/scms/view/materials.fxml");
    }

    @FXML
    private void handleViewRequests() {
        setActiveButton(btnRequests);
        loadPage("/com/scms/view/requests.fxml");
    }

    @FXML
    private void handleViewRecipes() {
        setActiveButton(btnRecipes);
        loadPage("/com/scms/view/recipes.fxml");
    }

    @FXML
    private void handleReports() {
        try {
            java.net.URL reportUrl = getClass().getResource("/com/scms/view/report_dialog.fxml");
            if (reportUrl == null) {
                System.err.println("Report dialog resource missing");
                return;
            }
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(reportUrl);
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            // apply theme based on preferences
            addAppStylesheet(scene);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Izvještaji");
            stage.setScene(scene);
            stage.initOwner(contentArea.getScene().getWindow());
            stage.show();
        } catch (Exception ex) {
            System.err.println("Failed to open reports window: " + ex.getMessage());
        }
    }

    @FXML
    private void handleManageUsers() {
        setActiveButton(btnUsers);
        loadPage("/com/scms/view/users.fxml");
    }

    @FXML
    private void handleStatisticsOverview() {
        setActiveButton(btnStatistics);
        loadPage("/com/scms/view/statistics.fxml");
    }

    @FXML
    private void handleExit() {
        System.exit(0);
    }

    // helper to mark active menu button using CSS class
    private void setActiveButton(Button active) {
        Button[] buttons = new Button[]{btnDashboard, btnWarehouse, btnRequests, btnRecipes, btnReports, btnUsers, btnStatistics};
        for (Button b : buttons) {
            if (b == null) continue;
            if (b.equals(active)) {
                if (!b.getStyleClass().contains("menu-active")) b.getStyleClass().add("menu-active");
            } else {
                b.getStyleClass().removeAll("menu-active");
            }
        }
    }

    // reusable page loader
    public void loadPage(String fxmlPath) {
        try {
            java.net.URL pageUrl = getClass().getResource(fxmlPath);
            if (pageUrl == null) {
                System.err.println("Missing page resource: " + fxmlPath);
                return;
            }
            Parent view = FXMLLoader.load(pageUrl);
            // ensure loaded page background is transparent so the main app background shows through
            try { if (view != null) view.setStyle("-fx-background-color: transparent;"); } catch (Exception ignore) {}
            // clear and set single child
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            System.err.println("Failed to load page '" + fxmlPath + "': " + e.getMessage());
        }
    }
}
