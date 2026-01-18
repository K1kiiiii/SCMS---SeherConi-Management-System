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
import com.scms.util.LoadingOverlay;
import java.util.concurrent.atomic.AtomicInteger;

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
        // show overlay while dashboard bootstraps
        LoadingOverlay.show(titleLabel);
        // pending load counter: cardsGrid load + critical card + tasks (one of admin/worker) — start at 1 for initial card load
        AtomicInteger pendingLoads = new AtomicInteger(1);
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

            // start loading critical card and tasks; increment pendingLoads for each async op
            // we already have 1 pending for initial bootstrap; add 2 more if both will run
            boolean willLoadCritical = !(currentRole.equals("RADNIK") || currentRole.equals("WORKER"));
            boolean willLoadTasks = !(currentRole.equals("MAGACIONER") || currentRole.equals("WAREHOUSE_STAFF"));
            int additional = 0;
            if (willLoadCritical) additional++;
            if (willLoadTasks) additional++;
            pendingLoads.addAndGet(additional);

            // initial bootstrap completed: decrement the initial pending count
            if (pendingLoads.decrementAndGet() == 0) {
                LoadingOverlay.hide(titleLabel);
            }

            // populate fixed critical materials card (UI-only)
            try { if (willLoadCritical) populateCriticalCard(() -> { if (pendingLoads.decrementAndGet() == 0) LoadingOverlay.hide(titleLabel); });
            } catch (Exception ex) { logError(ex, "populateCriticalCard"); if (pendingLoads.decrementAndGet() == 0) LoadingOverlay.hide(titleLabel); }

            // role-driven tasks handling
            try {
                if (willLoadTasks) handleTasksForRole(() -> { if (pendingLoads.decrementAndGet() == 0) LoadingOverlay.hide(titleLabel); });
                else {
                    if (pendingLoads.decrementAndGet() == 0) LoadingOverlay.hide(titleLabel);
                }
            } catch (Exception ex) { logError(ex, "handleTasksForRole"); if (pendingLoads.decrementAndGet() == 0) LoadingOverlay.hide(titleLabel); }

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
            // ensure overlay hidden on failure
            LoadingOverlay.hide(titleLabel);
            Throwable ex = loadTask.getException();
            System.err.println("Failed to load dashboard data: " + (ex != null ? ex.getMessage() : "unknown"));
            if (ex != null) ex.printStackTrace(System.err);
            // fallback: show empty/default cards
            List<VBox> fallback = new ArrayList<>();
            fallback.add(createCardUI("Ukupno sirovina", "0"));
            fallback.add(createCardUI("Zahtjevi (ovaj mjesec)", "0"));
            populateGrid(fallback);

            try { handleTasksForRole(null); } catch (Exception ex2) { logError(ex2, "handleTasksForRole"); }
            try { populateCriticalCard(null); } catch (Exception ex2) { logError(ex2, "populateCriticalCard"); }
         });

        Thread t = new Thread(loadTask, "dashboard-loader");
        t.setDaemon(true);
        t.start();
    }

    // Modified populateCriticalCard that accepts a Runnable callback executed on completion (on FX thread)
    private void populateCriticalCard(Runnable onComplete) {
        // run DB work off FX thread to avoid UI blocking
        javafx.concurrent.Task<List<Material>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Material> call() throws Exception {
                return materialDao.findMaterialsBelowMinimum();
            }
        };
        task.setOnSucceeded(ev -> {
            List<Material> below = task.getValue();
            criticalCardContent.getChildren().clear();
            if (below == null || below.isEmpty()) {
                Label ok = new Label("✔ Trenutno nema kritičnih sirovina");
                ok.getStyleClass().add("ok-message");
                ok.setMaxWidth(Double.MAX_VALUE);
                criticalCardContent.getChildren().add(ok);
            } else {
                for (Material m : below) {
                    HBox row = new HBox(8);
                    Label warn = new Label("⚠");
                    warn.getStyleClass().add("critical-item");
                    Label name = new Label(m.getName());
                    name.getStyleClass().add("task-title");
                    name.setWrapText(true);
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
            }
            if (onComplete != null) onComplete.run();
        });
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            logError(ex, "populateCriticalCard");
            criticalCardContent.getChildren().clear();
            Label err = new Label("Greška pri učitavanju sirovina");
            criticalCardContent.getChildren().add(err);
            if (onComplete != null) onComplete.run();
        });
        Thread th = new Thread(task, "critical-card-loader");
        th.setDaemon(true);
        th.start();
    }

    // Modified handleTasksForRole that accepts a completion callback
    private void handleTasksForRole(Runnable onComplete) {
        if (currentRole.equals("MAGACIONER") || currentRole.equals("WAREHOUSE_STAFF")) {
            if (tasksBox != null) tasksBox.setManaged(false);
            if (tasksBox != null) tasksBox.setVisible(false);
            if (onComplete != null) onComplete.run();
            return;
        }

        if (currentRole.equals("ADMIN")) {
            if (tasksHeader != null) tasksHeader.setText("Zadaci u toku");
            // load in progress tasks async
            loadInProgressTasks(onComplete);
            return;
        }

        // default / RADNIK: load assigned tasks
        loadWorkerTasks(onComplete);
    }

    // Load tasks with status = IN_PROGRESS for ADMIN
    private void loadInProgressTasks(Runnable onComplete) {
        if (tasksList == null) { if (onComplete != null) onComplete.run(); return; }
        tasksList.getChildren().clear();
        javafx.concurrent.Task<List<Task>> task = new javafx.concurrent.Task<>() {
            @Override protected List<Task> call() throws Exception {
                List<Task> all = taskDao.findAll();
                List<Task> inProgress = new ArrayList<>();
                for (Task t : all) if (t.getStatus() != null && t.getStatus().equalsIgnoreCase("IN_PROGRESS")) inProgress.add(t);
                return inProgress;
            }
        };
        task.setOnSucceeded(ev -> {
            List<Task> inProgress = task.getValue();
            DateTimeFormatter dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy 'u' HH:mm");
            for (Task t : inProgress) {
                String recipeName = "Recept: " + t.getRecipeId();
                try { Optional<Recipe> r = recipeDao.findById(t.getRecipeId()); if (r.isPresent()) recipeName = r.get().getName(); } catch (SQLException ex) { logError(ex, "fetchRecipeName"); }

                String workerName = "Nepoznato";
                if (t.getAssignedTo() != null) {
                    try { Optional<User> uu = userDao.findById(t.getAssignedTo()); if (uu.isPresent()) workerName = uu.get().getUsername(); } catch (SQLException ex) { logError(ex, "fetchWorkerName"); }
                }

                String target = String.format("%.2f %s", t.getQuantityTarget(), t.getUnit() != null ? t.getUnit() : "");
                String started = t.getStartedAt() != null ? t.getStartedAt().format(dateTimeFmt) : "-";

                HBox row = new HBox(8);
                row.getStyleClass().add("task-row");
                Label title = new Label(recipeName);
                title.getStyleClass().add("task-title");
                title.setWrapText(true);
                title.setMaxWidth(Double.MAX_VALUE);
                title.setStyle("-fx-font-weight:700; -fx-text-fill:#4A3428; -fx-font-size:13px;");

                Label meta = new Label(String.format("%s — %s — Započeto: %s", workerName, target, started));
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
            if (tasksScroll != null) tasksScroll.setVvalue(0);
            if (onComplete != null) onComplete.run();
        });
        task.setOnFailed(ev -> {
            logError(task.getException(), "loadInProgressTasks");
            if (onComplete != null) onComplete.run();
        });
        Thread th = new Thread(task, "inprogress-tasks-loader");
        th.setDaemon(true);
        th.start();
    }

    // Build card data (title + computed value) depending on role — runs on background thread
    private List<CardInfo> buildCardDataForRole() throws SQLException {
        List<CardInfo> cards = new ArrayList<>();
        User u = RoleManager.getLoggedInUser();
        String role = u != null && u.getRole() != null ? u.getRole().toUpperCase() : "";

        switch (role) {
            case "ADMIN":
                // fetch lists once and reuse sizes to avoid multiple DB calls
                List<Material> allMaterials = materialDao.findAll();
                List<User> allUsers = userDao.findAll();
                List<Material> below = materialDao.findMaterialsBelowMinimum();

                cards.add(new CardInfo("Ukupno sirovina", String.valueOf(allMaterials.size())));
                cards.add(new CardInfo("Zahtjevi za sirovine (ovaj mjesec)", String.valueOf(countRequestsThisMonth())));
                cards.add(new CardInfo("Aktivni korisnici", String.valueOf(allUsers.size())));
                if (below.isEmpty()) {
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", "0"));
                } else {
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", String.valueOf(below.size())));
                }
                break;
            case "MAGACIONER": // local DB role is 'magacioner' — treat as WAREHOUSE_STAFF
            case "WAREHOUSE_STAFF":
                List<Material> below2 = materialDao.findMaterialsBelowMinimum();
                cards.add(new CardInfo("Na čekanju - zahtjevi za sirovine", String.valueOf(countPendingRequests())));
                if (below2.isEmpty()) {
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", "0"));
                } else {
                    cards.add(new CardInfo("Sirovine ispod minimalne zalihe", String.valueOf(below2.size())));
                }
                cards.add(new CardInfo("Izdane sirovine danas", String.valueOf(countIssuedToday())));
                break;
            case "RADNIK": // worker role mapping
            case "WORKER":
                cards.add(new CardInfo("Moji zahtjevi na čekanju", String.valueOf(countMyPendingRequests(u))));
                cards.add(new CardInfo("Izdane sirovine (ovaj mjesec)", String.valueOf(countIssuedThisMonthForUser(u))));
                cards.add(new CardInfo("Najčešće korištena sirovina", mostFrequentlyUsedMaterialForUser(u)));
                break;
            default:
                // default: show admin-like overview but safe
                cards.add(new CardInfo("Ukupno sirovina", String.valueOf(safeCountMaterials())));
                cards.add(new CardInfo("Zahtjevi (ovaj mjesec)", String.valueOf(safeCountRequestsThisMonth())));
                break;
        }
        return cards;
    }

    // Count issued this month for a specific user (assignments assigned_at)
    private int countIssuedThisMonthForUser(User u) {
        if (u == null) return 0;
        try {
            List<Assignment> assignments = assignmentDao.findByUserId(u.getId());
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

    // Most frequently used material for a specific user (by assignments quantity)
    private String mostFrequentlyUsedMaterialForUser(User u) {
        if (u == null) return "-";
        try {
            List<Assignment> assignments = assignmentDao.findByUserId(u.getId());
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
            return "-";
        }
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
    private void loadWorkerTasks(Runnable onComplete) {
        User u = RoleManager.getLoggedInUser();
        if (tasksList != null) tasksList.getChildren().clear();
        if (u == null) {
            System.out.println("loadWorkerTasks: no logged-in user");
            if (onComplete != null) onComplete.run();
            return;
        }

        javafx.concurrent.Task<List<Task>> task = new javafx.concurrent.Task<>() {
            @Override protected List<Task> call() throws Exception {
                return taskDao.findByAssignedUser(u.getId());
            }
        };
        task.setOnSucceeded(ev -> {
            List<Task> tasks = task.getValue();
            if (tasks == null || tasks.isEmpty()) {
                if (tasksBox != null) {
                    tasksBox.setManaged(true);
                    tasksBox.setVisible(true);
                }
                Label empty = new Label("Trenutno nema dodijeljenih zadataka");
                empty.getStyleClass().add("ok-message");
                empty.setMaxWidth(Double.MAX_VALUE);
                tasksList.getChildren().add(empty);
                if (tasksScroll != null) tasksScroll.setVvalue(0);
                if (onComplete != null) onComplete.run();
                return;
            } else {
                if (tasksBox != null) {
                    tasksBox.setManaged(true);
                    tasksBox.setVisible(true);
                }
            }

            for (Task t : tasks) {
                VBox card = createTaskCard(t);
                tasksList.getChildren().add(card);
            }
            if (tasksScroll != null) tasksScroll.setVvalue(0);
            if (onComplete != null) onComplete.run();
        });
        task.setOnFailed(ev -> {
            logError(task.getException(), "loadWorkerTasks");
            if (onComplete != null) onComplete.run();
        });
        Thread th = new Thread(task, "worker-tasks-loader");
        th.setDaemon(true);
        th.start();
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

        // Pending badge: shown when there are requests and they are not yet confirmed
        Label pendingBadge = new Label("ČEKA POTVRDU SIROVINA");
        pendingBadge.getStyleClass().addAll("task-badge", "pending-badge");
        pendingBadge.setVisible(false);
        pendingBadge.setManaged(false);

        HBox titleRow = new HBox(8);
        titleRow.getChildren().addAll(title, pendingBadge);
        HBox.setHgrow(title, Priority.ALWAYS);

        Label target = new Label(String.format("Cilj: %.2f %s", t.getQuantityTarget(), t.getUnit() != null ? t.getUnit() : ""));
        target.getStyleClass().add("task-meta");
        Label status = new Label("Status: " + (t.getStatus()!=null? t.getStatus():"PENDING"));
        status.getStyleClass().add("task-meta");

        HBox actions = new HBox(8);

        Button requestBtn = new Button("Zatraži");
        requestBtn.setOnAction(evt -> onRequestIngredients(t));
        // rely on CSS classes for button visuals
        // Disable until we check whether the user already has requests for this task
        requestBtn.setDisable(true);

        Button startBtn = new Button("Započni");
        Label startHint = new Label();
        startHint.getStyleClass().add("task-hint");
        startHint.setWrapText(true);
        startHint.setMaxWidth(220);
        // default disable until we check DAO
        startBtn.setDisable(true);

        // Async check: count assignments for THIS user and whether they're confirmed
        User currentUser = RoleManager.getLoggedInUser();
        final Integer currentUserId = currentUser != null ? currentUser.getId() : null;
        javafx.concurrent.Task<Void> checkTask = new javafx.concurrent.Task<>() {
            int total = 0;
            boolean confirmed = false;
            @Override protected Void call() throws Exception {
                try {
                    total = assignmentDao.countTaskAssignmentsForUser(t.getId(), currentUserId);
                    confirmed = assignmentDao.areTaskAssignmentsConfirmed(t.getId());
                } catch (SQLException ex) {
                    // bubble up
                    throw ex;
                }
                return null;
            }
            @Override protected void succeeded() {
                // Update UI based on DAO results
                if (total == 0) {
                    startBtn.setDisable(true);
                    startHint.setText("Morate prvo poslati zahtjev za sirovine prije započinjanja zadatka.");
                    pendingBadge.setVisible(false);
                    pendingBadge.setManaged(false);
                    // No existing requests by this user -> allow sending requests
                    requestBtn.setDisable(false);
                } else if (!confirmed) {
                    startBtn.setDisable(true);
                    startHint.setText("Čekanje potvrde magacionera za zahtjeve.");
                    pendingBadge.setVisible(true);
                    pendingBadge.setManaged(true);
                    // user already sent requests -> disable request button
                    requestBtn.setDisable(true);
                } else {
                    startBtn.setDisable(false);
                    startHint.setText("");
                    pendingBadge.setVisible(false);
                    pendingBadge.setManaged(false);
                    // requests were confirmed; user shouldn't be able to re-request
                    requestBtn.setDisable(true);
                }
            }
            @Override protected void failed() {
                startBtn.setDisable(true);
                startHint.setText("Greška pri provjeri zahtjeva. Pokušajte ponovo.");
                pendingBadge.setVisible(false);
                pendingBadge.setManaged(false);
                // On error keep request button disabled to avoid duplicate attempts
                requestBtn.setDisable(true);
                logError(getException(), "checkTaskAssignments");
            }
        };
        Thread checkThread = new Thread(checkTask, "check-assignments-" + t.getId());
        checkThread.setDaemon(true);
        checkThread.start();

        startBtn.setOnAction(evt -> {
            try {
                // final runtime guard: ensure the current user created at least one request and all are confirmed
                int total = assignmentDao.countTaskAssignmentsForUser(t.getId(), currentUserId);
                if (total == 0) {
                    showAlert(Alert.AlertType.WARNING, "Nije moguće započeti", "Vi niste poslali zahtjeve za sirovine vezane uz ovaj zadatak.");
                    return;
                }
                boolean confirmed = assignmentDao.areTaskAssignmentsConfirmed(t.getId());
                if (!confirmed) {
                    showAlert(Alert.AlertType.WARNING, "Nije moguće započeti", "Magacioner mora prvo potvrditi zahtjeve za sirovine vezane uz ovaj zadatak.");
                    return;
                }

                boolean ok = taskDao.updateStatus(t.getId(), "IN_PROGRESS");
                if (ok) {
                    t.setStatus("IN_PROGRESS");
                    loadWorkerTasks();
                }
            } catch (SQLException ex) {
                logError(ex, "updateStatus");
                showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri provjeri zahtjeva ili ažuriranju statusa: " + ex.getMessage());
            }
        });

        Button finishBtn = new Button("Završi");
        finishBtn.setOnAction(evt -> onCompleteTask(t));

        // show buttons depending on status
        String st = t.getStatus() != null ? t.getStatus().toUpperCase() : "PENDING";
        if (st.equals("PENDING")) {
            actions.getChildren().addAll(startBtn, requestBtn);
            // also show the hint label next to buttons
            actions.getChildren().add(startHint);
        } else if (st.equals("IN_PROGRESS")) {
            actions.getChildren().add(finishBtn);
        }

        // Make recipe title clickable to show details (admin and worker task views)
        final int recipeIdForCard = t.getRecipeId();
        title.setOnMouseClicked(ev -> RecipeDetailDialog.show(recipeIdForCard));
        // Change cursor affordance
        title.setStyle(title.getStyle() + " -fx-cursor: hand;");
        // prevent button clicks from propagating to title
        requestBtn.setOnMouseClicked(evt -> { evt.consume(); onRequestIngredients(t); });
        startBtn.setOnMouseClicked(evt -> { evt.consume(); /* action handled in setOnAction */ });
        finishBtn.setOnMouseClicked(evt -> { evt.consume(); onCompleteTask(t); });

        VBox card = new VBox(6, titleRow, target, status, actions);
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

            // Validation: user should not create multiple requests for the same task
            try {
                int existing = assignmentDao.countTaskAssignmentsForUser(t.getId(), current.getId());
                if (existing > 0) {
                    showAlert(Alert.AlertType.WARNING, "Zahtjev već poslan", "Već ste poslali zahtjeve za ovaj zadatak. Nemoguće je poslati dupli zahtjev.");
                    return;
                }
            } catch (SQLException ex) {
                logError(ex, "checkExistingRequests");
                showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri provjeri postojećih zahtjeva: " + ex.getMessage());
                return;
            }

            int created = 0;
            for (RecipeItem ri : items) {
                Assignment a = new Assignment();
                a.setUserId(current.getId());
                a.setMaterialId(ri.getMaterialId());
                double qty = ri.getQuantity() * t.getQuantityTarget();
                a.setQuantity(qty);
                a.setStatus("PENDING");
                a.setNotes("Za zadatak id=" + t.getId() + ", recept=" + recipe.getName());
                try {
                    assignmentDao.createRequest(a);
                    created++;
                } catch (SQLException ex) {
                    logError(ex, "createRequest");
                    // continue creating other requests if possible, but inform user
                }
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

    // Backwards-compatible no-arg wrappers for places that call the original methods
    private void populateCriticalCard() { populateCriticalCard(null); }
    private void handleTasksForRole() { handleTasksForRole(null); }
    private void loadInProgressTasks() { loadInProgressTasks(null); }
    private void loadWorkerTasks() { loadWorkerTasks(null); }
 }
