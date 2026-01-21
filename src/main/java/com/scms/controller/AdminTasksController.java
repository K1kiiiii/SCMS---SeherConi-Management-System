package com.scms.controller;

import com.scms.dao.TaskDao;
import com.scms.dao.UserDao;
import com.scms.dao.RecipeDao;
import com.scms.model.Task;
import com.scms.model.User;
import com.scms.model.Recipe;
import com.scms.util.RoleManager;
import com.scms.util.LoadingOverlay;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin tasks view — optimized to precompute display values off the FX thread
 * to avoid per-cell DB calls and UI freezes when the task list is large.
 */
public class AdminTasksController {

    // Use a lightweight DTO for table rows with precomputed display properties
    public static class TaskRow {
        private final SimpleIntegerProperty id = new SimpleIntegerProperty();
        private final SimpleStringProperty recipe = new SimpleStringProperty();
        private final SimpleStringProperty assigned = new SimpleStringProperty();
        private final SimpleStringProperty target = new SimpleStringProperty();
        private final SimpleStringProperty status = new SimpleStringProperty();
        private final SimpleStringProperty started = new SimpleStringProperty();

        public TaskRow(int id, String recipe, String assigned, String target, String status, String started) {
            this.id.set(id);
            this.recipe.set(recipe);
            this.assigned.set(assigned);
            this.target.set(target);
            this.status.set(status);
            this.started.set(started);
        }

        public SimpleIntegerProperty idProperty() { return id; }
        public SimpleStringProperty recipeProperty() { return recipe; }
        public SimpleStringProperty assignedProperty() { return assigned; }
        public SimpleStringProperty targetProperty() { return target; }
        public SimpleStringProperty statusProperty() { return status; }
        public SimpleStringProperty startedProperty() { return started; }
    }

    @FXML private TableView<TaskRow> tasksTable;
    @FXML private TableColumn<TaskRow, Integer> colId;
    @FXML private TableColumn<TaskRow, String> colRecipe;
    @FXML private TableColumn<TaskRow, String> colAssigned;
    @FXML private TableColumn<TaskRow, String> colTarget;
    @FXML private TableColumn<TaskRow, String> colStatus;
    @FXML private TableColumn<TaskRow, String> colStarted;
    @FXML private Button refreshBtn;

    private final TaskDao taskDao = new TaskDao();
    private final UserDao userDao = new UserDao();
    private final RecipeDao recipeDao = new RecipeDao();
    private final ObservableList<TaskRow> items = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // guard: only admins
        if (!RoleManager.isAdmin()) {
            if (tasksTable != null) tasksTable.setPlaceholder(new Label("Pristup zabranjen"));
            return;
        }

        // wire columns to TaskRow properties (no DB calls here)
        colId.setCellValueFactory(cd -> cd.getValue().idProperty().asObject());
        colRecipe.setCellValueFactory(cd -> cd.getValue().recipeProperty());
        colAssigned.setCellValueFactory(cd -> cd.getValue().assignedProperty());
        colTarget.setCellValueFactory(cd -> cd.getValue().targetProperty());
        colStatus.setCellValueFactory(cd -> cd.getValue().statusProperty());
        colStarted.setCellValueFactory(cd -> cd.getValue().startedProperty());

        tasksTable.setItems(items);
        loadTasksAsync();
    }

    private void loadTasksAsync() {
        LoadingOverlay.show(tasksTable);
        javafx.concurrent.Task<List<Task>> task = new javafx.concurrent.Task<>() {
            @Override protected List<Task> call() throws Exception { return taskDao.findAll(); }
        };

        task.setOnSucceeded(ev -> {
            List<Task> all = task.getValue();
            // prepare mappings in background thread to avoid many DB calls on FX thread
            try {
                List<Recipe> recs = recipeDao.findAll();
                Map<Integer, String> recipeMap = recs.stream().collect(Collectors.toMap(Recipe::getId, Recipe::getName));
                List<User> users = userDao.findAll();
                Map<Integer, String> userMap = users.stream().collect(Collectors.toMap(User::getId, User::getUsername));

                DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy 'u' HH:mm");
                // build TaskRow list
                List<TaskRow> rows = all.stream().map(t -> {
                    // recipeId is a primitive int in Task, so don't compare to null — just lookup with default
                    String rname = recipeMap.getOrDefault(t.getRecipeId(), "#" + t.getRecipeId());
                    String uname = (t.getAssignedTo() != null) ? userMap.getOrDefault(t.getAssignedTo(), "-") : "-";
                    String target = String.format("%.2f %s", t.getQuantityTarget(), t.getUnit() == null ? "" : t.getUnit());
                    String st = t.getStatus() == null ? "-" : t.getStatus();
                    String started = (t.getStartedAt() == null) ? "-" : t.getStartedAt().format(f);
                    return new TaskRow(t.getId(), rname, uname, target, st, started);
                }).collect(Collectors.toList());

                // update UI on FX thread
                javafx.application.Platform.runLater(() -> { items.setAll(rows); LoadingOverlay.hide(tasksTable); });
            } catch (Exception ex) {
                // fallback: map minimally and set
                List<TaskRow> rows = all.stream().map(t -> new TaskRow(t.getId(), "#" + t.getRecipeId(), t.getAssignedTo() == null ? "-" : String.valueOf(t.getAssignedTo()), String.format("%.2f %s", t.getQuantityTarget(), t.getUnit() == null ? "" : t.getUnit()), t.getStatus() == null ? "-" : t.getStatus(), t.getStartedAt() == null ? "-" : t.getStartedAt().toString())).collect(Collectors.toList());
                javafx.application.Platform.runLater(() -> { items.setAll(rows); LoadingOverlay.hide(tasksTable); });
            }
        });

        task.setOnFailed(ev -> { LoadingOverlay.hide(tasksTable); Throwable ex = task.getException(); if (ex!=null) ex.printStackTrace(); });
        Thread t = new Thread(task, "admin-tasks-loader"); t.setDaemon(true); t.start();
    }

    @FXML
    private void onRefresh() {
        loadTasksAsync();
    }
}
