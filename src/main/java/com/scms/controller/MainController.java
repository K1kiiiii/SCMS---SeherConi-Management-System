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

    @FXML private Menu systemMenu;
    @FXML private Menu usersMenu;
    @FXML private Menu warehouseMenu;
    @FXML private Menu workMenu;
    @FXML private Menu statisticsMenu;
    @FXML private Menu helpMenu;

    @FXML private MenuItem logoutMenuItem;
    @FXML private MenuItem exitMenuItem;

    @FXML private MenuItem manageUsersMenuItem;

    @FXML private MenuItem viewItemsMenuItem;
    @FXML private MenuItem createItemMenuItem;
    @FXML private MenuItem viewWarehouseMenuItem;
    @FXML private MenuItem goodsInMenuItem;
    @FXML private MenuItem goodsOutMenuItem;
    @FXML private MenuItem ordersMenuItem;
    @FXML private MenuItem viewRequestsMenuItem;

    @FXML private MenuItem workOrdersMenuItem;
    @FXML private MenuItem updateWorkOrderStatusMenuItem;
    @FXML private MenuItem materialRequestMenuItem;
    @FXML private MenuItem reportsMenuItem;

    @FXML private MenuItem statisticsOverviewMenuItem;

    @FXML private MenuItem aboutMenuItem;

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
    }

    private void applyRoleVisibility() {
        // puni access za admina
        if (RoleManager.isAdmin()) {
            workMenu.setVisible(false); //iako bi mogao imati pristup, adminu ovo aposlutno ne treba,, valjda
            return;
        }

        if (RoleManager.isMagacioner()) {
            // magacioner nece upravljati radnicima i poslovnom statistikom
            usersMenu.setVisible(false);
            statisticsMenu.setVisible(false);
            workMenu.setVisible(false);
            return;
        }

        if (RoleManager.isRadnik()) {
            usersMenu.setVisible(false);
            statisticsMenu.setVisible(false);}
    }

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

    @FXML
    private void handleViewRequests(ActionEvent event) {
        try {
            Parent view = FXMLLoader.load(
                    getClass().getResource("/com/scms/view/requests.fxml")
            );
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // funkcije to be implemented

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

    @FXML
    private void handleStatisticsOverview(ActionEvent event) {
        System.out.println("Statistics Overview clicked");
    }

    @FXML
    private void handleAbout(ActionEvent event) {
        System.out.println("About clicked");
    }
}
