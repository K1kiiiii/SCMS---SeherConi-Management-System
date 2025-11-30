package com.scms.controller;

import com.scms.model.User;
import com.scms.util.RoleManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {

    @FXML private Label welcomeLabel;
    @FXML private Label userInfoLabel;
    @FXML private StackPane contentArea;

    // Menus
    @FXML private Menu systemMenu;
    @FXML private Menu usersMenu;
    @FXML private Menu warehouseMenu;
    @FXML private Menu workMenu;
    @FXML private Menu statisticsMenu;
    @FXML private Menu helpMenu;

    // System menu items
    @FXML private MenuItem logoutMenuItem;
    @FXML private MenuItem exitMenuItem;

    // Users
    @FXML private MenuItem manageUsersMenuItem;

    // Warehouse
    @FXML private MenuItem viewItemsMenuItem;
    @FXML private MenuItem createItemMenuItem;
    @FXML private MenuItem viewWarehouseMenuItem;
    @FXML private MenuItem goodsInMenuItem;
    @FXML private MenuItem goodsOutMenuItem;
    @FXML private MenuItem ordersMenuItem;

    // Work orders
    @FXML private MenuItem workOrdersMenuItem;
    @FXML private MenuItem updateWorkOrderStatusMenuItem;
    @FXML private MenuItem materialRequestMenuItem;
    @FXML private MenuItem reportsMenuItem;

    // Statistics
    @FXML private MenuItem statisticsOverviewMenuItem;

    // Help
    @FXML private MenuItem aboutMenuItem;

    @FXML
    public void initialize() {
        // Show who is logged in
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
    }

    private void applyRoleVisibility() {
        // Admin → full access
        if (RoleManager.isAdmin()) {
            return;
        }

        // Magacioner
        if (RoleManager.isMagacioner()) {
            // No user management, no statistics, no work-order menu
            usersMenu.setVisible(false);
            statisticsMenu.setVisible(false);
            workMenu.setVisible(false);
            return;
        }

        // Radnik
        if (RoleManager.isRadnik()) {
            // Only work orders + reports + basic system/help
            usersMenu.setVisible(false);
            statisticsMenu.setVisible(false);
            warehouseMenu.setVisible(false);
        }
    }

    // System
    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            RoleManager.setLoggedInUser(null);

            Parent loginView = FXMLLoader.load(
                    getClass().getResource("/com/scms/view/login.fxml")
            );
            Scene scene =  new Scene(loginView);
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
    private void handleExit(ActionEvent event) {
        System.out.println("Exit clicked");
        System.exit(0);
    }

    // Users
    @FXML
    private void handleManageUsers(ActionEvent event) {
        try {
            Parent view = FXMLLoader.load(
                    getClass().getResource("/com/scms/view/users.fxml")
            );
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // warehouse
    @FXML
    private void handleViewItems(ActionEvent event) {
        try {
            Parent view = FXMLLoader.load(
                    getClass().getResource("/com/scms/view/materials.fxml")
            );
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Work orders
    @FXML
    private void handleWorkOrders(ActionEvent event) {
        System.out.println("Work Orders clicked");
    }

    @FXML
    private void handleUpdateWorkOrderStatus(ActionEvent event) {
        System.out.println("Update Work Order Status clicked");
    }

    @FXML
    private void handleMaterialRequest(ActionEvent event) {
        System.out.println("Material Request clicked");
    }

    @FXML
    private void handleReports(ActionEvent event) {
        System.out.println("Reports clicked");
    }

    // Statistics
    @FXML
    private void handleStatisticsOverview(ActionEvent event) {
        System.out.println("Statistics Overview clicked");
    }

    // Help
    @FXML
    private void handleAbout(ActionEvent event) {
        System.out.println("About clicked");
    }
}
