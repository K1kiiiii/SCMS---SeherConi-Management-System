package com.scms.controller;

import com.scms.dao.TaskDao;
import com.scms.dao.UserDao;
import com.scms.dao.RecipeDao;
import com.scms.model.Task;
import com.scms.model.User;
import com.scms.model.Recipe;
import com.scms.util.DialogUtils;
import com.scms.util.RoleManager;
import com.scms.util.LoadingOverlay;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class AdminTasksController {

    @FXML private TableView<Task> tasksTable;
    @FXML private TableColumn<Task, Integer> colId;
    @FXML private TableColumn<Task, String> colRecipe;
    @FXML private TableColumn<Task, String> colAssigned;
    @FXML private TableColumn<Task, String> colTarget;
    @FXML private TableColumn<Task, String> colStatus;
    @FXML private TableColumn<Task, String> colStarted;
    @FXML private Button refreshBtn;

    private final TaskDao taskDao = new TaskDao();
    private final UserDao userDao = new UserDao();
    private final RecipeDao recipeDao = new RecipeDao();
    private final ObservableList<Task> items = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // guard: only admins
        if (!RoleManager.isAdmin()) {
            tasksTable.setPlaceholder(new Label("Pristup zabranjen"));
            return;
        }

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colRecipe.setCellValueFactory(cd -> {
            Task t = cd.getValue();
            try { Optional<Recipe> r = recipeDao.findById(t.getRecipeId()); return new javafx.beans.property.SimpleStringProperty(r.map(Recipe::getName).orElse("#" + t.getRecipeId())); } catch (Exception ex) { return new javafx.beans.property.SimpleStringProperty("#"+t.getRecipeId()); }
        });
        colAssigned.setCellValueFactory(cd -> {
            Task t = cd.getValue();
            try { Optional<User> u = userDao.findById(t.getAssignedTo()); return new javafx.beans.property.SimpleStringProperty(u.map(User::getUsername).orElse("-")); } catch (Exception ex) { return new javafx.beans.property.SimpleStringProperty("-"); }
        });
        colTarget.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f %s", cd.getValue().getQuantityTarget(), cd.getValue().getUnit() == null ? "" : cd.getValue().getUnit())));
        colStatus.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getStatus()));
        colStarted.setCellValueFactory(cd -> {
            if (cd.getValue().getStartedAt() == null) return new javafx.beans.property.SimpleStringProperty("-");
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy 'u' HH:mm");
            return new javafx.beans.property.SimpleStringProperty(cd.getValue().getStartedAt().format(f));
        });

        tasksTable.setItems(items);
        loadTasksAsync();
    }

    private void loadTasksAsync() {
        LoadingOverlay.show(tasksTable);
        javafx.concurrent.Task<List<Task>> task = new javafx.concurrent.Task<>() {
            @Override protected List<Task> call() throws Exception { return taskDao.findAll(); }
        };
        task.setOnSucceeded(ev -> { items.setAll(task.getValue()); LoadingOverlay.hide(tasksTable); });
        task.setOnFailed(ev -> { LoadingOverlay.hide(tasksTable); Throwable ex = task.getException(); if (ex!=null) ex.printStackTrace(); });
        Thread t = new Thread(task, "admin-tasks-loader"); t.setDaemon(true); t.start();
    }

    @FXML
    private void onRefresh() {
        loadTasksAsync();
    }
}

