package com.scms.controller;

import com.scms.dao.AssignmentDao;
import com.scms.dao.MaterialDao;
import com.scms.dao.UserDao;
import com.scms.model.Assignment;
import com.scms.model.Material;
import com.scms.model.User;
import com.scms.util.RoleManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    @FXML private GridPane cardsGrid;

    private final MaterialDao materialDao = new MaterialDao();
    private final AssignmentDao assignmentDao = new AssignmentDao();
    private final UserDao userDao = new UserDao();

    private static final double LOW_STOCK_THRESHOLD_DEFAULT = 5.0;

    // simple holder for computed card data (safe to create on background thread)
    private static class CardInfo {
        final String title;
        final String value;
        CardInfo(String title, String value) { this.title = title; this.value = value; }
    }

    @FXML
    public void initialize() {
        User u = RoleManager.getLoggedInUser();
        if (u != null) {
            String role = u.getRole() != null ? u.getRole() : "";
            titleLabel.setText("Dobrodošli, " + u.getUsername());
            subtitleLabel.setText("Uloga: " + role + " — Pregled sistema i ključne statistike");
        } else {
            titleLabel.setText("Dobrodošli");
            subtitleLabel.setText("Pregled sistema i ključne statistike");
        }

        // Use a JavaFX Task to run DB work off the FX thread and update UI on success
        Task<List<CardInfo>> loadTask = new Task<>() {
            @Override
            protected List<CardInfo> call() throws Exception {
                return buildCardDataForRole();
            }
        };

        loadTask.setOnSucceeded(evt -> {
            List<CardInfo> cardData = loadTask.getValue();
            List<VBox> uiCards = new ArrayList<>();
            for (CardInfo ci : cardData) uiCards.add(createCardUI(ci.title, ci.value));
            populateGrid(uiCards);
        });

        loadTask.setOnFailed(evt -> {
            Throwable ex = loadTask.getException();
            System.err.println("Failed to load dashboard data: " + (ex != null ? ex.getMessage() : "unknown"));
            if (ex != null) ex.printStackTrace(System.err);
            // fallback: show empty/default cards
            List<VBox> fallback = new ArrayList<>();
            fallback.add(createCardUI("Ukupno sirovina", "0"));
            fallback.add(createCardUI("Zahtjevi (ovaj mjesec)", "0"));
            populateGrid(fallback);
        });

        Thread t = new Thread(loadTask, "dashboard-loader");
        t.setDaemon(true);
        t.start();
    }

    // Build card data (title + computed value) depending on role — runs on background thread
    private List<CardInfo> buildCardDataForRole() throws SQLException {
        List<CardInfo> cards = new ArrayList<>();
        User u = RoleManager.getLoggedInUser();
        String role = u != null && u.getRole() != null ? u.getRole().toUpperCase() : "";

        switch (role) {
            case "ADMIN":
                cards.add(new CardInfo("Ukupno sirovina", String.valueOf(materialDao.findAll().size())));
                cards.add(new CardInfo("Zahtjevi za sirovine (ovaj mjesec)", String.valueOf(countRequestsThisMonth())));
                cards.add(new CardInfo("Aktivni korisnici", String.valueOf(userDao.findAll().size())));
                cards.add(new CardInfo("Sirovine ispod minimalne zalihe", String.valueOf(countLowStock())));
                break;
            case "MAGACIONER": // local DB role is 'magacioner' — treat as WAREHOUSE_STAFF
            case "WAREHOUSE_STAFF":
                cards.add(new CardInfo("Na čekanju - zahtjevi za sirovine", String.valueOf(countPendingRequests())));
                cards.add(new CardInfo("Sirovine ispod minimalne zalihe", String.valueOf(countLowStock())));
                cards.add(new CardInfo("Izdane sirovine danas", String.valueOf(countIssuedToday())));
                break;
            case "RADNIK": // worker role mapping
            case "WORKER":
                cards.add(new CardInfo("Moji zahtjevi na čekanju", String.valueOf(countMyPendingRequests(u))));
                cards.add(new CardInfo("Izdane sirovine (ovaj mjesec)", String.valueOf(countIssuedThisMonth())));
                cards.add(new CardInfo("Najčešće korištena sirovina", mostFrequentlyUsedMaterial()));
                break;
            default:
                // default: show admin-like overview but safe
                cards.add(new CardInfo("Ukupno sirovina", String.valueOf(safeCountMaterials())));
                cards.add(new CardInfo("Zahtjevi (ovaj mjesec)", String.valueOf(safeCountRequestsThisMonth())));
                break;
        }
        return cards;
    }

    // Populate GridPane in row-major order — must be called on FX thread
    private void populateGrid(List<VBox> cards) {
        cardsGrid.getChildren().clear();
        cardsGrid.getColumnConstraints().clear();
        // keep 4 columns as layout base
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cardsGrid.getColumnConstraints().add(cc);
        }

        for (int i = 0; i < cards.size(); i++) {
            int col = i % 4;
            int row = i / 4;
            cardsGrid.add(cards.get(i), col, row);
        }
    }

    // Create card UI nodes on the FX thread
    private VBox createCardUI(String title, String value) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#666666;");
        Label valueLbl = new Label(value != null ? value : "0");
        valueLbl.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");

        VBox card = new VBox(6, titleLbl, valueLbl);
        card.setStyle("-fx-background-color:#ffffff; -fx-padding:12; -fx-border-radius:6; -fx-background-radius:6; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 6, 0, 0, 2);");
        return card;
    }

    // Metric helpers (all safe to call on background thread)
    private int safeCountMaterials() {
        try { return materialDao.findAll().size(); } catch (SQLException ex) { return 0; }
    }

    private int safeCountRequestsThisMonth() {
        try { return countRequestsThisMonth(); } catch (SQLException ex) { return 0; }
    }

    private int countRequestsThisMonth() throws SQLException {
        List<Assignment> assignments = assignmentDao.findAll();
        LocalDate now = LocalDate.now();
        int y = now.getYear();
        int m = now.getMonthValue();
        return (int) assignments.stream().filter(a -> {
            LocalDateTime dt = a.getAssignedAt();
            if (dt == null) return false;
            return dt.getYear() == y && dt.getMonthValue() == m;
        }).count();
    }

    private int countLowStock() throws SQLException {
        List<Material> materials = materialDao.findAll();
        return (int) materials.stream().filter(m -> m.getQuantity() < LOW_STOCK_THRESHOLD_DEFAULT).count();
    }

    private int countPendingRequests() throws SQLException {
        List<Assignment> assignments = assignmentDao.findAll();
        return (int) assignments.stream().filter(a -> a.getStatus() != null && a.getStatus().equalsIgnoreCase("PENDING")).count();
    }

    private int countMyPendingRequests(User u) {
        if (u == null) return 0;
        try {
            List<Assignment> assignments = assignmentDao.findByUserId(u.getId());
            return (int) assignments.stream().filter(a -> a.getStatus() != null && a.getStatus().equalsIgnoreCase("PENDING")).count();
        } catch (SQLException ex) { return 0; }
    }

    private int countIssuedToday() throws SQLException {
        List<Assignment> assignments = assignmentDao.findAll();
        LocalDate today = LocalDate.now();
        return (int) assignments.stream().filter(a -> {
            LocalDateTime dt = a.getAssignedAt();
            if (dt == null) return false;
            return dt.toLocalDate().isEqual(today);
        }).count();
    }

    private int countIssuedThisMonth() {
        try {
            List<Assignment> assignments = assignmentDao.findAll();
            LocalDate now = LocalDate.now();
            int y = now.getYear();
            int m = now.getMonthValue();
            return (int) assignments.stream().filter(a -> {
                LocalDateTime dt = a.getAssignedAt();
                if (dt == null) return false;
                return dt.getYear() == y && dt.getMonthValue() == m;
            }).count();
        } catch (SQLException ex) { return 0; }
    }

    private String mostFrequentlyUsedMaterial() {
        try {
            List<Assignment> assignments = assignmentDao.findAll();
            if (assignments.isEmpty()) return "-";
            return assignments.stream()
                    .collect(Collectors.groupingBy(Assignment::getMaterialId, Collectors.summingDouble(Assignment::getQuantity)))
                    .entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue))
                    .map(e -> {
                        try {
                            Material m = materialDao.findById(e.getKey()).orElse(null);
                            return m != null ? m.getName() : ("id:" + e.getKey());
                        } catch (SQLException ex) { return "id:" + e.getKey(); }
                    }).orElse("-");
        } catch (SQLException ex) {
            System.err.println("Failed to compute most frequently used material: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return "-";
        }
    }
}
