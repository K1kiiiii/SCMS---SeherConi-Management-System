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
import javafx.scene.layout.Region;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.scms.service.NotificationService;
import com.scms.util.DialogUtils;

public class DashboardController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    @FXML private GridPane cardsGrid;
    @FXML private VBox tasksList;
    @FXML private ScrollPane tasksScroll;
    @FXML private VBox tasksBox;
    @FXML private Label tasksHeader;
    @FXML private VBox criticalCardContent;
    @FXML private VBox criticalCard;
    @FXML private ScrollPane criticalScroll;

    private final MaterialDao materialDao = new MaterialDao();
    private final AssignmentDao assignmentDao = new AssignmentDao();
    private final UserDao userDao = new UserDao();
    private final TaskDao taskDao = new TaskDao();
    private final RecipeDao recipeDao = new RecipeDao();

    // no hard-coded threshold — use per-material minimum from DB
    private NotificationService notificationService;

    // cached role for the current session
    private String currentRole = "";

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
            this.currentRole = role != null ? role.toUpperCase() : "";
        } else {
            titleLabel.setText("Dobrodošli");
            subtitleLabel.setText("Pregled sistema i ključne statistike");
            this.currentRole = "";
        }

        // Set tasks area visibility and header synchronously to avoid UI flash
        if (tasksBox != null) {
            if (currentRole.equals("MAGACIONER") || currentRole.equals("WAREHOUSE_STAFF")) {
                tasksBox.setManaged(false);
                tasksBox.setVisible(false);
            } else {
                tasksBox.setManaged(true);
                tasksBox.setVisible(true);
            }
        }
        if (tasksHeader != null) {
            if (currentRole.equals("ADMIN")) tasksHeader.setText("Zadaci u toku");
            else tasksHeader.setText("Moji zadaci");
        }

        // Hide critical materials card for RADNIK/WORKER roles
        if (criticalCard != null) {
            if (currentRole.equals("RADNIK") || currentRole.equals("WORKER")) {
                criticalCard.setManaged(false);
                criticalCard.setVisible(false);
            } else {
                criticalCard.setManaged(true);
                criticalCard.setVisible(true);
            }
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

            // role-driven tasks handling
            try {
                handleTasksForRole();
            } catch (Exception ex) { logError(ex, "handleTasksForRole"); }

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

            try { handleTasksForRole(); } catch (Exception ex2) { logError(ex2, "handleTasksForRole"); }
            try { populateCriticalCard(); } catch (Exception ex2) { logError(ex2, "populateCriticalCard"); }
         });

        Thread t = new Thread(loadTask, "dashboard-loader");
        t.setDaemon(true);
        t.start();
    }

    // Decide what to do with the tasks card based on role
    private void handleTasksForRole() {
        if (currentRole.equals("MAGACIONER") || currentRole.equals("WAREHOUSE_STAFF")) {
            // remove tasksBox entirely for magacioner
            if (tasksBox != null) tasksBox.setManaged(false);
            if (tasksBox != null) tasksBox.setVisible(false);
            return;
        }

        if (currentRole.equals("ADMIN")) {
            // admin: show all IN_PROGRESS tasks
            if (tasksHeader != null) tasksHeader.setText("Zadaci u toku");
            loadInProgressTasks();
            return;
        }

        // default / RADNIK: load assigned tasks
        loadWorkerTasks();
    }

    // Load tasks with status = IN_PROGRESS for ADMIN
    private void loadInProgressTasks() {
        if (tasksList == null) return;
        tasksList.getChildren().clear();
        try {
            List<Task> all = taskDao.findAll();
            List<Task> inProgress = new ArrayList<>();
            for (Task t : all) if (t.getStatus() != null && t.getStatus().equalsIgnoreCase("IN_PROGRESS")) inProgress.add(t);
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
            for (Task t : inProgress) {
                String recipeName = "Recept: " + t.getRecipeId();
                try { Optional<Recipe> r = recipeDao.findById(t.getRecipeId()); if (r.isPresent()) recipeName = r.get().getName(); } catch (SQLException ex) { logError(ex, "fetchRecipeName"); }

                String workerName = "Nepoznato";
                if (t.getAssignedTo() != null) {
                    try { Optional<User> uu = userDao.findById(t.getAssignedTo()); if (uu.isPresent()) workerName = uu.get().getUsername(); } catch (SQLException ex) { logError(ex, "fetchWorkerName"); }
                }

                String target = String.format("%.2f %s", t.getQuantityTarget(), t.getUnit() != null ? t.getUnit() : "");
                String started = t.getStartedAt() != null ? t.getStartedAt().format(timeFmt) : "-";

                HBox row = new HBox(8);
                row.getStyleClass().add("task-row");
                Label title = new Label(recipeName);
                title.getStyleClass().add("task-title");
                title.setWrapText(true);
                title.setMaxWidth(Double.MAX_VALUE);
                title.setStyle("-fx-font-weight:700; -fx-text-fill:#4A3428; -fx-font-size:13px;");

                Label meta = new Label(String.format("%s — %s — započeto u %s", workerName, target, started));
                meta.getStyleClass().add("task-meta");
                meta.setWrapText(true);
                meta.setMaxWidth(Double.MAX_VALUE);
                meta.setStyle("-fx-text-fill:#6b4f3f; -fx-font-size:12px;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                Label badge = new Label("U TOKU");
                badge.getStyleClass().add("task-badge");
                row.getChildren().addAll(title, spacer, meta, badge);
                tasksList.getChildren().add(row);
            }
            // ensure scroll shows top
            if (tasksScroll != null) tasksScroll.setVvalue(0);
        } catch (SQLException ex) {
            logError(ex, "loadInProgressTasks");
        }
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
        cardsGrid.getRowConstraints().clear();
         // keep 4 columns as layout base
         for (int i = 0; i < 4; i++) {
             ColumnConstraints cc = new ColumnConstraints();
             cc.setPercentWidth(25);
             cc.setHgrow(Priority.ALWAYS);
             cardsGrid.getColumnConstraints().add(cc);
         }
        // ensure rows have consistent height so card contents (titles) are visible
        int rows = (cards.size() + 3) / 4; // ceil division
        for (int r = 0; r < rows; r++) {
            javafx.scene.layout.RowConstraints rc = new javafx.scene.layout.RowConstraints();
            rc.setMinHeight(140);
            rc.setPrefHeight(160);
            rc.setVgrow(Priority.ALWAYS);
            cardsGrid.getRowConstraints().add(rc);
        }

         for (int i = 0; i < cards.size(); i++) {
             int col = i % 4;
             int row = i / 4;
             VBox card = cards.get(i);
             // make cards grow to fill column width to avoid label truncation
             card.setMaxWidth(Double.MAX_VALUE);
             card.setPrefWidth(Double.MAX_VALUE);
             GridPane.setHgrow(card, Priority.ALWAYS);
             GridPane.setFillWidth(card, true);
             cardsGrid.add(card, col, row);
         }
    }

    // Load tasks for logged-in worker and populate tasksList — placed early to avoid forward-reference warnings
    private void loadWorkerTasks() {
        User u = RoleManager.getLoggedInUser();
        if (tasksList != null) tasksList.getChildren().clear();
        if (u == null) return;
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "";
        if (!role.equals("RADNIK") && !role.equals("WORKER")) return;

        try {
            List<Task> tasks = taskDao.findByAssignedUser(u.getId());
            for (Task t : tasks) {
                VBox card = createTaskCard(t);
                tasksList.getChildren().add(card);
            }
            if (tasksScroll != null) tasksScroll.setVvalue(0);
        } catch (SQLException ex) {
            logError(ex, "loadWorkerTasks");
        }
    }

    // Create card UI nodes on the FX thread
    private VBox createCardUI(String title, String value) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("card-title");
        // allow wrapping and full width so long titles don't truncate with ellipsis
        titleLbl.setWrapText(true);
        // fallback inline styling to guarantee readability if stylesheet isn't loaded/applied
        titleLbl.setStyle("-fx-font-size:14px; -fx-font-weight:700; -fx-text-fill:#4A3428;");

        Label valueLbl = new Label(value != null ? value : "0");
        valueLbl.getStyleClass().add("card-value");
        valueLbl.setStyle("-fx-font-size:18px; -fx-font-weight:800; -fx-text-fill:#5C3D2E;");

        VBox card = new VBox(6, titleLbl, valueLbl);
        card.getStyleClass().addAll("stat-card", "dashboard-card");
        // give programmatic cards a minimum/preferred height so labels have room (matches grid CSS)
        card.setMinHeight(140);
        card.setPrefHeight(160);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(Double.MAX_VALUE);

         // Bind label max widths to the card width so they wrap within available space
         titleLbl.maxWidthProperty().bind(card.widthProperty().subtract(12));
         valueLbl.maxWidthProperty().bind(card.widthProperty().subtract(12));

         return card;
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
        title.getStyleClass().add("task-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-font-weight:700; -fx-text-fill:#4A3428; -fx-font-size:13px;");

        Label target = new Label(String.format("Cilj: %.2f %s", t.getQuantityTarget(), t.getUnit() != null ? t.getUnit() : ""));
        target.getStyleClass().add("task-meta");
        Label status = new Label("Status: " + (t.getStatus()!=null? t.getStatus():"PENDING"));
        status.getStyleClass().add("task-meta");

        HBox actions = new HBox(8);

        Button requestBtn = new Button("Zatraži");
        requestBtn.setOnAction(evt -> onRequestIngredients(t));
        // rely on CSS classes for button visuals

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
        card.getStyleClass().add("task-card");
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
        // apply app styling to the dialog
        com.scms.util.DialogUtils.styleDialog(d);
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
        DialogUtils.styleAlert(a);
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
                ok.getStyleClass().add("ok-message");
                ok.setMaxWidth(Double.MAX_VALUE);
                // rely on CSS for ok-message styling
                criticalCardContent.getChildren().add(ok);
                return;
            }

            for (Material m : below) {
                HBox row = new HBox(8);
                Label warn = new Label("⚠");
                warn.getStyleClass().add("critical-item");
                Label name = new Label(m.getName());
                name.getStyleClass().add("task-title");
                name.setWrapText(true);
                // bind name width to container so long names wrap nicely inside the scroll area
                name.maxWidthProperty().bind(criticalCardContent.widthProperty().subtract(48));
                String unit = m.getUnit() != null ? m.getUnit() : "";
                Label qty = new Label(String.format("%.2f %s / min %.2f %s", m.getQuantity(), unit, m.getMinimumQuantity(), unit));
                qty.getStyleClass().add("critical-item");
                qty.setStyle("-fx-text-fill:#D23B3B; -fx-font-weight:700;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                row.getChildren().addAll(warn, name, spacer, qty);
                row.getStyleClass().add("task-row");
                criticalCardContent.getChildren().add(row);
            }
        } catch (SQLException ex) {
            logError(ex, "populateCriticalCard");
            Label err = new Label("Greška pri učitavanju sirovina");
            criticalCardContent.getChildren().add(err);
        }
    }

}
