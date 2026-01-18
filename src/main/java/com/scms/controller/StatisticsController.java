package com.scms.controller;

import com.scms.config.DatabaseConfig;
import com.scms.dao.UserDao;
import com.scms.model.User;
import com.scms.util.RoleManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class StatisticsController {

    @FXML private HBox adminControls;
    @FXML private ComboBox<User> cbUsers;

    @FXML private VBox globalStatsBox;
    @FXML private Label lblTotalUsers;
    @FXML private Label lblTotalMaterials;
    @FXML private Label lblPendingAssignments;
    @FXML private Label lblConfirmedAssignments;
    @FXML private Label lblCompletedTasks;

    @FXML private PieChart globalAssignmentsChart;
    @FXML private PieChart globalTasksChart;

    @FXML private VBox personalStatsBox;
    @FXML private Label lblUserName;
    @FXML private Label lblUserPendingAssignments;
    @FXML private Label lblUserConfirmedAssignments;
    @FXML private Label lblUserCompletedTasks;

    @FXML private PieChart personalAssignmentsChart;
    @FXML private PieChart personalTasksChart;

    private final UserDao userDao = new UserDao();

    @FXML
    public void initialize() {
        try {
            User current = RoleManager.getLoggedInUser();
            boolean isAdmin = RoleManager.isAdmin();

            // Admin: show global stats + user filter
            if (isAdmin) {
                if (adminControls != null) { adminControls.setVisible(true); adminControls.setManaged(true); }
                if (globalStatsBox != null) { globalStatsBox.setVisible(true); globalStatsBox.setManaged(true); }
                loadGlobalStats();
                populateUsers();
                // default: show aggregated personal stats for ALL users
                showPersonalStatsFor(null);
                return;
            }

            // Non-admin: hide admin controls and global box, show personal stats for logged-in user
            if (adminControls != null) { adminControls.setVisible(false); adminControls.setManaged(false); }
            if (globalStatsBox != null) { globalStatsBox.setVisible(false); globalStatsBox.setManaged(false); }

            if (current != null) {
                showPersonalStatsFor(current.getId());
            } else {
                // no user: clear
                showPersonalStatsFor(-1);
            }

        } catch (Exception ex) {
            System.err.println("Failed initializing statistics controller: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadGlobalStats() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            // total users
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) lblTotalUsers.setText(String.valueOf(rs.getInt(1))); }
            }

            // total materials
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM materials")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) lblTotalMaterials.setText(String.valueOf(rs.getInt(1))); }
            }

            // assignments by status
            int pending = 0, confirmed = 0, rejected = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT status, COUNT(*) AS cnt FROM assignments GROUP BY status")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String status = rs.getString("status");
                        int cnt = rs.getInt("cnt");
                        if (status == null) continue;
                        if (status.equalsIgnoreCase("PENDING")) pending = cnt;
                        else if (status.equalsIgnoreCase("CONFIRMED")) confirmed = cnt;
                        else if (status.equalsIgnoreCase("REJECTED")) rejected = cnt;
                    }
                }
            }
            lblPendingAssignments.setText(String.valueOf(pending));
            lblConfirmedAssignments.setText(String.valueOf(confirmed));

            // update global assignments pie
            ObservableList<PieChart.Data> gaData = FXCollections.observableArrayList();
            gaData.add(new PieChart.Data("PENDING", pending));
            gaData.add(new PieChart.Data("CONFIRMED", confirmed));
            gaData.add(new PieChart.Data("REJECTED", rejected));
            if (globalAssignmentsChart != null) {
                globalAssignmentsChart.setData(gaData);
                globalAssignmentsChart.setLegendVisible(true);
            }

            // completed tasks
            int completedTasks = 0, inProgress = 0, pendingTasks = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT status, COUNT(*) FROM tasks GROUP BY status")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String st = rs.getString(1);
                        int cnt = rs.getInt(2);
                        if (st == null) continue;
                        if (st.equalsIgnoreCase("COMPLETED")) completedTasks = cnt;
                        else if (st.equalsIgnoreCase("IN_PROGRESS")) inProgress = cnt;
                        else if (st.equalsIgnoreCase("PENDING")) pendingTasks = cnt;
                    }
                }
            }
            lblCompletedTasks.setText(String.valueOf(completedTasks));

            ObservableList<PieChart.Data> gtData = FXCollections.observableArrayList();
            gtData.add(new PieChart.Data("COMPLETED", completedTasks));
            gtData.add(new PieChart.Data("IN_PROGRESS", inProgress));
            gtData.add(new PieChart.Data("PENDING", pendingTasks));
            if (globalTasksChart != null) {
                globalTasksChart.setData(gtData);
                globalTasksChart.setLegendVisible(true);
            }

        } catch (Exception ex) {
            System.err.println("Failed loading global stats: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void populateUsers() {
        try {
            List<User> users = userDao.findAll();
            ObservableList<User> items = FXCollections.observableArrayList(users);
            if (cbUsers != null) {
                cbUsers.setItems(items);
                cbUsers.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                    @Override
                    protected void updateItem(User item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.getUsername() + " (" + (item.getRole() == null ? "" : item.getRole()) + ")");
                    }
                });
                cbUsers.setButtonCell(new javafx.scene.control.ListCell<>() {
                    @Override
                    protected void updateItem(User item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.getUsername());
                    }
                });
                cbUsers.getSelectionModel().clearSelection();
            }
        } catch (Exception ex) {
            System.err.println("Failed populating users combobox: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    private void onShowForSelectedUser() {
        try {
            User sel = cbUsers == null ? null : cbUsers.getSelectionModel().getSelectedItem();
            if (sel == null) {
                // treat null selection as ALL for admins
                showPersonalStatsFor(null);
            } else {
                showPersonalStatsFor(sel.getId());
            }
        } catch (Exception ex) {
            System.err.println("Failed showing stats for selected user: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void showPersonalStatsFor(Integer userId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean admin = RoleManager.isAdmin();
            String username = "-";

            if (userId != null && userId >= 0) {
                User u = userDao.findById(userId).orElse(null);
                if (u != null) username = u.getUsername();
            } else if (userId == null) {
                // admin requested ALL users
                username = "Svi korisnici";
            } else {
                // userId == -1 or other negative: use logged in user
                User cur = RoleManager.getLoggedInUser();
                if (cur != null) { userId = cur.getId(); username = cur.getUsername(); }
            }

            lblUserName.setText(username == null ? "-" : username);

            // assignments for this user by status (or all users if admin and userId==null)
            if (userId == null && admin) {
                int pending = 0, confirmed = 0, rejected = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT status, COUNT(*) AS cnt FROM assignments GROUP BY status")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String status = rs.getString("status");
                            int cnt = rs.getInt("cnt");
                            if (status == null) continue;
                            if (status.equalsIgnoreCase("PENDING")) pending = cnt;
                            else if (status.equalsIgnoreCase("CONFIRMED")) confirmed = cnt;
                            else if (status.equalsIgnoreCase("REJECTED")) rejected = cnt;
                        }
                    }
                }
                lblUserPendingAssignments.setText(String.valueOf(pending));
                lblUserConfirmedAssignments.setText(String.valueOf(confirmed));

                ObservableList<PieChart.Data> paData = FXCollections.observableArrayList();
                paData.add(new PieChart.Data("PENDING", pending));
                paData.add(new PieChart.Data("CONFIRMED", confirmed));
                paData.add(new PieChart.Data("REJECTED", rejected));
                if (personalAssignmentsChart != null) personalAssignmentsChart.setData(paData);

                int completedTasks = 0, inProgress = 0, pendingTasks = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT status, COUNT(*) FROM tasks GROUP BY status")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String st = rs.getString(1);
                            int cnt = rs.getInt(2);
                            if (st == null) continue;
                            if (st.equalsIgnoreCase("COMPLETED")) completedTasks = cnt;
                            else if (st.equalsIgnoreCase("IN_PROGRESS")) inProgress = cnt;
                            else if (st.equalsIgnoreCase("PENDING")) pendingTasks = cnt;
                        }
                    }
                }
                lblUserCompletedTasks.setText(String.valueOf(completedTasks));
                ObservableList<PieChart.Data> ptData = FXCollections.observableArrayList();
                ptData.add(new PieChart.Data("COMPLETED", completedTasks));
                ptData.add(new PieChart.Data("IN_PROGRESS", inProgress));
                ptData.add(new PieChart.Data("PENDING", pendingTasks));
                if (personalTasksChart != null) personalTasksChart.setData(ptData);

            } else {
                int uid = userId == null ? -1 : userId;
                int pending = 0, confirmed = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT status, COUNT(*) AS cnt FROM assignments WHERE user_id = ? GROUP BY status")) {
                    ps.setInt(1, uid);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String status = rs.getString("status");
                            int cnt = rs.getInt("cnt");
                            if (status == null) continue;
                            if (status.equalsIgnoreCase("PENDING")) pending = cnt;
                            else if (status.equalsIgnoreCase("CONFIRMED")) confirmed = cnt;
                        }
                    }
                }
                lblUserPendingAssignments.setText(String.valueOf(pending));
                lblUserConfirmedAssignments.setText(String.valueOf(confirmed));

                ObservableList<PieChart.Data> paData = FXCollections.observableArrayList();
                paData.add(new PieChart.Data("PENDING", pending));
                paData.add(new PieChart.Data("CONFIRMED", confirmed));
                if (personalAssignmentsChart != null) personalAssignmentsChart.setData(paData);

                int completedTasks = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM tasks WHERE assigned_to = ? AND status = 'COMPLETED'")) {
                    ps.setInt(1, uid);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) completedTasks = rs.getInt(1); }
                }
                lblUserCompletedTasks.setText(String.valueOf(completedTasks));

                ObservableList<PieChart.Data> ptData = FXCollections.observableArrayList();
                ptData.add(new PieChart.Data("COMPLETED", completedTasks));
                ptData.add(new PieChart.Data("OTHER", Math.max(0, 1))); // placeholder to show pie if single slice
                if (personalTasksChart != null) personalTasksChart.setData(ptData);
            }

        } catch (Exception ex) {
            System.err.println("Failed loading personal stats: " + ex.getMessage());
            ex.printStackTrace();
            // clear to safe defaults
            lblUserPendingAssignments.setText("0");
            lblUserConfirmedAssignments.setText("0");
            lblUserCompletedTasks.setText("0");
            if (personalAssignmentsChart != null) personalAssignmentsChart.setData(FXCollections.emptyObservableList());
            if (personalTasksChart != null) personalTasksChart.setData(FXCollections.emptyObservableList());
        }
    }
}
