package com.scms.controller;

import com.scms.dao.TaskDao;
import com.scms.dao.UserDao;
import com.scms.model.Task;
import com.scms.model.User;
import com.scms.util.RoleManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller for the "Assign Task" dialog. Used by admin to assign a recipe task to a worker.
 */
public class AssignTaskController {

    @FXML private ComboBox<User> workerCombo;
    @FXML private TextField quantityField;
    @FXML private TextField unitField;
    @FXML private DatePicker deadlinePicker;

    private final UserDao userDao = new UserDao();
    private final TaskDao taskDao = new TaskDao();

    private int recipeId;

    // called by opener to set the recipe id
    public void setRecipeId(int recipeId) { this.recipeId = recipeId; }

    @FXML
    public void initialize() {
        // load workers into combo
        try {
            List<User> all = userDao.findAll();
            ObservableList<User> workers = FXCollections.observableArrayList();
            for (User u : all) {
                String r = u.getRole() != null ? u.getRole().toUpperCase() : "";
                if (r.equals("RADNIK") || r.equals("WORKER")) workers.add(u);
            }
            workerCombo.setItems(workers);
            workerCombo.setCellFactory(cb -> new ListCell<>() {
                @Override public void updateItem(User item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getUsername());
                }
            });
            workerCombo.setButtonCell(new ListCell<>() { @Override public void updateItem(User item, boolean empty) { super.updateItem(item, empty); setText(empty||item==null?null:item.getUsername()); } });
        } catch (SQLException ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Greška", "Ne mogu učitati listu radnika.");
        }
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    @FXML
    private void onConfirm() {
        User selected = workerCombo.getValue();
        if (selected == null) { showAlert(Alert.AlertType.WARNING, "Validacija", "Odaberite radnika."); return; }
        String qtxt = quantityField.getText();
        if (qtxt == null || qtxt.trim().isEmpty()) { showAlert(Alert.AlertType.WARNING, "Validacija", "Unesite ciljanu količinu."); return; }
        double qty;
        try { qty = Double.parseDouble(qtxt.trim()); } catch (NumberFormatException ex) { showAlert(Alert.AlertType.WARNING, "Validacija", "Količina mora biti broj."); return; }

        Task t = new Task();
        t.setRecipeId(recipeId);
        t.setAssignedTo(selected.getId());
        User creator = RoleManager.getLoggedInUser();
        t.setCreatedBy(creator != null ? creator.getId() : null);
        t.setQuantityTarget(qty);
        t.setUnit(unitField.getText());
        LocalDate dl = deadlinePicker.getValue();
        t.setDeadline(dl);
        t.setStatus("PENDING");

        try {
            taskDao.create(t);
            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Zadatak je dodan i dodijeljen.");
            closeWindow();
        } catch (SQLException ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Greška", "Ne mogu spremiti zadatak: " + ex.getMessage());
        }
    }

    private void closeWindow() {
        Stage s = (Stage) quantityField.getScene().getWindow();
        s.close();
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
