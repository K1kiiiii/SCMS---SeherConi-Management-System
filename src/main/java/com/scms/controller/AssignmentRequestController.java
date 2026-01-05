package com.scms.controller;

import com.scms.model.Material;
import com.scms.model.User;
import com.scms.service.AssignmentRequestService;
import com.scms.service.MaterialService;
import com.scms.service.ServiceException;
import com.scms.util.RoleManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;

public class AssignmentRequestController {
    @FXML private ComboBox<Material> materialCombo;
    @FXML private TextField quantityField;
    @FXML private TextArea notesArea;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final MaterialService materialService = new MaterialService();
    private final AssignmentRequestService requestService = new AssignmentRequestService();

    @FXML
    public void initialize() {
        try {
            List<Material> materials = materialService.listMaterials();
            ObservableList<Material> items = FXCollections.observableArrayList(materials);
            materialCombo.setItems(items);
            materialCombo.setConverter(new javafx.util.StringConverter<Material>() {
                @Override
                public String toString(Material object) { return object == null ? "" : object.getName(); }
                @Override
                public Material fromString(String string) { return null; }
            });
        } catch (ServiceException ex) {
            showError("Greška", "Ne mogu učitati materijale: " + ex.getMessage());
        }

        saveButton.setOnAction(this::handleSave);
        cancelButton.setOnAction(e -> ((Stage) cancelButton.getScene().getWindow()).close());
    }

    private void handleSave(ActionEvent event) {
        Material m = materialCombo.getValue();
        if (m == null) { showWarning("Odabir materijala", "Molimo odaberite materijal."); return; }
        double qty;
        try {
            qty = Double.parseDouble(quantityField.getText());
        } catch (NumberFormatException ex) {
            showWarning("Količina", "Molimo unesite ispravnu brojčanu vrijednost za količinu.");
            return;
        }

        User u = RoleManager.getLoggedInUser();
        if (u == null) { showError("Autentikacija", "Niste prijavljeni."); return; }

        try {
            requestService.createRequest(u.getId(), m.getId(), qty, notesArea.getText());
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Zahtjev poslan");
            info.setHeaderText(null);
            info.setContentText("Zahtjev za materijal je poslan i ima status PENDING.");
            info.showAndWait();
            ((Stage) saveButton.getScene().getWindow()).close();
        } catch (ServiceException ex) {
            showError("Greška pri slanju", ex.getMessage());
        }
    }

    private void showWarning(String title, String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}

