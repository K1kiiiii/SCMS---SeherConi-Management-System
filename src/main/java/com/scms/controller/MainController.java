package com.scms.controller;

import com.scms.model.User;
import com.scms.util.RoleManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {

    @FXML private Label welcomeLabel;
    @FXML private Label userInfoLabel;
    @FXML private StackPane contentArea;

    // side menu and buttons
    @FXML private VBox sideMenu;
    @FXML private Button btnDashboard;
    @FXML private Button btnWarehouse;
    @FXML private Button btnRequests;
    @FXML private Button btnRecipes;
    @FXML private Button btnUsers;
    @FXML private Button btnStatistics;

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
            String css = getClass().getResource("/com/scms/css/dark-theme.css").toExternalForm();
            if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
        } catch (Exception ex) {
            System.err.println("Could not load app stylesheet: " + ex.getMessage());
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
            return;
        }

        if (RoleManager.isRadnik()) {
            // radnik ne upravlja korisnicima i statistikama
            btnUsers.setVisible(false);
            btnStatistics.setVisible(false);
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            RoleManager.setLoggedInUser(null);

            Parent loginView = FXMLLoader.load(
                    getClass().getResource("/com/scms/view/login.fxml")
            );
            javafx.scene.Scene scene =  new javafx.scene.Scene(loginView);
            String css = getClass().getResource("/com/scms/css/dark-theme.css").toExternalForm();
            scene.getStylesheets().add(css);
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDashboard(ActionEvent event) {
        setActiveButton(btnDashboard);
        loadPage("/com/scms/view/dashboard.fxml");
    }

    @FXML
    private void handleViewItems(ActionEvent event) {
        setActiveButton(btnWarehouse);
        loadPage("/com/scms/view/materials.fxml");
    }

    @FXML
    private void handleViewRequests(ActionEvent event) {
        setActiveButton(btnRequests);
        loadPage("/com/scms/view/requests.fxml");
    }

    @FXML
    private void handleViewRecipes(ActionEvent event) {
        setActiveButton(btnRecipes);
        loadPage("/com/scms/view/recipes.fxml");
    }

    @FXML
    private void handleManageUsers(ActionEvent event) {
        setActiveButton(btnUsers);
        loadPage("/com/scms/view/users.fxml");
    }

    @FXML
    private void handleStatisticsOverview(ActionEvent event) {
        setActiveButton(btnStatistics);
        loadPage("/com/scms/view/statistics.fxml");
    }

    @FXML
    private void handleExit(ActionEvent event) {
        System.exit(0);
    }

    // helper to mark active menu button using CSS class
    private void setActiveButton(Button active) {
        Button[] buttons = new Button[]{btnDashboard, btnWarehouse, btnRequests, btnRecipes, btnUsers, btnStatistics};
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
            Parent view = FXMLLoader.load(getClass().getResource(fxmlPath));
            // clear and set single child
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
