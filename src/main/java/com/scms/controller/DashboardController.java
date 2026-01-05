package com.scms.controller;

import com.scms.dao.AssignmentDao;
import com.scms.dao.MaterialDao;
import com.scms.dao.UserDao;
import com.scms.dao.TaskDao;
import com.scms.dao.RecipeDao;
import com.scms.model.Assignment;
import com.scms.model.Material;
import com.scms.model.Task;
import com.scms.model.User;
import com.scms.model.Recipe;
import com.scms.model.RecipeItem;
import com.scms.util.RoleManager;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.scms.service.NotificationService;

public class DashboardController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    @FXML private GridPane cardsGrid;
    @FXML private FlowPane tasksPane;
    @FXML private VBox criticalCardContent;
    @FXML private VBox criticalCard;

    private final MaterialDao materialDao = new MaterialDao();
    private final AssignmentDao assignmentDao = new AssignmentDao();
    private final UserDao userDao = new UserDao();
    private final TaskDao taskDao = new TaskDao();
    private final RecipeDao recipeDao = new RecipeDao();

    // no hard-coded threshold — use per-material minimum from DB
    private NotificationService notificationService;

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
        javafx.concurrent.Task<List<CardInfo>> loadTask = new javafx.concurrent.Task<>() {
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

            // populate fixed critical materials card (UI-only)
            try {
                populateCriticalCard();
            } catch (Exception ex) { logError(ex, "populateCriticalCard"); }

            // load tasks for worker
            try { loadWorkerTasks(); } catch (Exception ex) { logError(ex, "loadWorkerTasks"); }
            // start notification service for warehouse staff / magacioner
            try {
                User u2 = RoleManager.getLoggedInUser();
                String r2 = u2 != null && u2.getRole() != null ? u2.getRole().toUpperCase() : "";
                if (r2.equals("MAGACIONER") || r2.equals("WAREHOUSE_STAFF")) {
                    if (notificationService == null) notificationService = new NotificationService();
                    notificationService.start();
                }
            } catch (Exception ex) { /* don't block dashboard */ }
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

            try { loadWorkerTasks(); } catch (Exception ex2) { logError(ex2, "loadWorkerTasks"); }
            try { populateCriticalCard(); } catch (Exception ex2) { logError(ex2, "populateCriticalCard"); }
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
                // add a card per material that is below its minimum
                List<Material> below = materialDao.findMaterialsBelowMinimum();
                if (below.isEmpty()) {
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", "0"));
                } else {
                    // single summary card for low-stock materials (details shown in fixed critical card)
                    int lowCount = materialDao.findMaterialsBelowMinimum().size();
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", String.valueOf(lowCount)));
                }
                break;
            case "MAGACIONER": // local DB role is 'magacioner' — treat as WAREHOUSE_STAFF
            case "WAREHOUSE_STAFF":
                cards.add(new CardInfo("Na čekanju - zahtjevi za sirovine", String.valueOf(countPendingRequests())));
                // for warehouse staff, show pending list of low-stock materials as individual cards
                List<Material> below2 = materialDao.findMaterialsBelowMinimum();
                if (below2.isEmpty()) {
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", "0"));
                } else {
                    // single summary card for low-stock materials (details shown in fixed critical card)
                    int lowCount2 = materialDao.findMaterialsBelowMinimum().size();
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", String.valueOf(lowCount2)));
                }
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
        titleLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#ffffff;");
        Label valueLbl = new Label(value != null ? value : "0");
        valueLbl.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");

        VBox card = new VBox(6, titleLbl, valueLbl);
        card.setStyle("-fx-background-color:#2b2b2b; -fx-padding:12; -fx-border-radius:6; -fx-background-radius:6; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 6, 0, 0, 2);");
        return card;
    }

    // Load tasks for logged-in worker and populate tasksPane — runs on FX thread but calls DAO synchronously (small lists)
    private void loadWorkerTasks() {
        User u = RoleManager.getLoggedInUser();
        tasksPane.getChildren().clear();
        if (u == null) return;
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "";
        if (!role.equals("RADNIK") && !role.equals("WORKER")) return;

        try {
            List<Task> tasks = taskDao.findByAssignedUser(u.getId());
            for (Task t : tasks) {
                VBox card = createTaskCard(t);
                tasksPane.getChildren().add(card);
            }
        } catch (SQLException ex) {
            logError(ex, "loadWorkerTasks");
        }
    }

    private VBox createTaskCard(Task t) {
        String recipeName = "Recept: " + t.getRecipeId();
        try {
            Optional<Recipe> r = recipeDao.findById(t.getRecipeId());
            if (r.isPresent()) recipeName = r.get().getName();
        } catch (SQLException ex) {
            logError(ex, "fetchRecipeName");
        }

        Label title = new Label(recipeName);
        title.setStyle("-fx-font-weight:bold;");
        Label target = new Label(String.format("Cilj: %.2f %s", t.getQuantityTarget(), t.getUnit() != null ? t.getUnit() : ""));
        Label status = new Label("Status: " + (t.getStatus()!=null? t.getStatus():"PENDING"));

        HBox actions = new HBox(8);

        Button requestBtn = new Button("Zatraži");
        requestBtn.setOnAction(evt -> onRequestIngredients(t));

        Button startBtn = new Button("Započni");
        startBtn.setOnAction(evt -> {
            try { boolean ok = taskDao.updateStatus(t.getId(), "IN_PROGRESS"); if (ok) { t.setStatus("IN_PROGRESS"); loadWorkerTasks(); } } catch (SQLException ex) { logError(ex, "updateStatus"); }
        });

        Button finishBtn = new Button("Završi");
        finishBtn.setOnAction(evt -> onCompleteTask(t));

        // show buttons depending on status
        String st = t.getStatus() != null ? t.getStatus().toUpperCase() : "PENDING";
        if (st.equals("PENDING")) {
            actions.getChildren().addAll(startBtn, requestBtn);
        } else if (st.equals("IN_PROGRESS")) {
            actions.getChildren().add(finishBtn);
        }

        VBox card = new VBox(6, title, target, status, actions);
        card.setStyle("-fx-background-color:#ffffff; -fx-padding:12; -fx-border-radius:6; -fx-background-radius:6;");
        card.setPrefWidth(320);
        return card;
    }

    private void onRequestIngredients(Task t) {
        // Create pending Assignment requests for each recipe item.
        // Assumption: RecipeItem.quantity represents quantity needed per 1 output unit; we scale by task.quantityTarget.
        try {
            Optional<Recipe> maybe = recipeDao.findById(t.getRecipeId());
            if (maybe.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Greška", "Ne mogu pronaći recept za zadatak.");
                return;
            }

            Recipe recipe = maybe.get();
            List<RecipeItem> items = recipe.getItems();
            if (items == null || items.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "Info", "Recept nema dodanih sirovina.");
                return;
            }

            User current = RoleManager.getLoggedInUser();
            if (current == null) { showAlert(Alert.AlertType.ERROR, "Greška", "Niste prijavljeni."); return; }

            int created = 0;
            for (RecipeItem ri : items) {
                Assignment a = new Assignment();
                a.setUserId(current.getId());
                a.setMaterialId(ri.getMaterialId());
                double qty = ri.getQuantity() * t.getQuantityTarget();
                a.setQuantity(qty);
                a.setStatus("PENDING");
                a.setNotes("Za zadatak id=" + t.getId() + ", recept=" + recipe.getName());
                assignmentDao.createRequest(a);
                created++;
            }

            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Poslano " + created + " zahtjeva za sirovine.");
        } catch (SQLException ex) {
            logError(ex, "onRequestIngredients");
            showAlert(Alert.AlertType.ERROR, "Greška", "Ne mogu poslati zahtjeve: " + ex.getMessage());
        }
    }

    private void onCompleteTask(Task t) {
        // prompt for produced quantity (simple input dialog)
        javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog();
        d.setTitle("Završi zadatak");
        d.setHeaderText(null);
        d.setContentText("Unesite proizvedenu količinu:");
        java.util.Optional<String> res = d.showAndWait();
        if (res.isPresent()) {
            try {
                Double produced = Double.parseDouble(res.get());
                boolean ok = taskDao.completeTask(t.getId(), produced);
                if (ok) loadWorkerTasks();
            } catch (NumberFormatException ex) { logError(ex, "parseProduced"); }
            catch (SQLException ex) { logError(ex, "completeTask"); }
        }
    }

    // small helper to show alerts on FX thread
    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void logError(Throwable ex, String context) {
        System.err.println("[ERROR] " + context + ": " + (ex != null ? ex.getMessage() : "null"));
        if (ex != null) ex.printStackTrace(System.err);
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
        // legacy helper retained but prefer DAO method
        return materialDao.findMaterialsBelowMinimum().size();
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

    // stop notification service when controller is garbage collected / app closes
    @FXML
    public void onClose() {
        if (notificationService != null) notificationService.stop();
    }

    // Populate the single fixed critical materials card
     private void populateCriticalCard() {
         // clear previous content
         criticalCardContent.getChildren().clear();
         try {
             List<Material> below = materialDao.findMaterialsBelowMinimum();
             if (below == null || below.isEmpty()) {
                 Label ok = new Label("✔ Trenutno nema kritičnih sirovina");
                 ok.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold; -fx-alignment: center; -fx-padding:20;");
                 criticalCardContent.getChildren().add(ok);
                 return;
             }

             for (Material m : below) {
                 HBox row = new HBox(8);
                 Label warn = new Label("⚠");
                 warn.setStyle("-fx-text-fill:#ff6b6b; -fx-font-size:14px;");
                 Label name = new Label(m.getName());
                 name.setStyle("-fx-font-weight:bold; -fx-text-fill:#ffffff;");
                 String unit = m.getUnit() != null ? m.getUnit() : "";
                 Label qty = new Label(String.format("%.2f %s / min %.2f %s", m.getQuantity(), unit, m.getMinimumQuantity(), unit));
                 qty.setStyle("-fx-text-fill:#ff8a80;");
                 Region spacer = new Region();
                 HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                 row.getChildren().addAll(warn, name, spacer, qty);
                 row.setStyle("-fx-padding:8; -fx-background-color: rgba(255, 92, 92, 0.04); -fx-background-radius:6;");
                 criticalCardContent.getChildren().add(row);
             }
         } catch (SQLException ex) {
             logError(ex, "populateCriticalCard");
             Label err = new Label("Greška pri učitavanju sirovina");
             criticalCardContent.getChildren().add(err);
         }
     }
}
