package com.scms.controller;

import com.scms.model.AssignmentRequest;
import com.scms.service.AssignmentRequestService;
import com.scms.service.MaterialService;
import com.scms.service.UserService;
import com.scms.service.ServiceException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

public class AssignmentListController {

    @FXML private TableView<AssignmentRequest> requestsTable;
    @FXML private TableColumn<AssignmentRequest, Integer> colId;
    @FXML private TableColumn<AssignmentRequest, String> colUser;
    @FXML private TableColumn<AssignmentRequest, String> colMaterial;
    @FXML private TableColumn<AssignmentRequest, Double> colQty;
    @FXML private TableColumn<AssignmentRequest, String> colNotes;
    @FXML private TableColumn<AssignmentRequest, String> colStatus;
    @FXML private TableColumn<AssignmentRequest, String> colRequestedAt;
    @FXML private Button refreshBtn;
    @FXML private Button approveBtn;
    @FXML private Button rejectBtn;
    @FXML private ComboBox<String> statusCombo;

    private final AssignmentRequestService requestService = new AssignmentRequestService();
    private final UserService userService = new UserService();
    private final MaterialService materialService = new MaterialService();

    private final ObservableList<AssignmentRequest> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        setupStatusFilter();
        loadBySelectedStatus();
        refreshBtn.setOnAction(e -> loadBySelectedStatus());
        approveBtn.setOnAction(e -> handleApprove());
        rejectBtn.setOnAction(e -> handleReject());
    }

    private void setupColumns() {
        colId.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().getId()));
        colUser.setCellValueFactory(cell -> {
            int uid = cell.getValue().getUserId();
            try { return new javafx.beans.property.SimpleStringProperty(userService.getUserById(uid).getUsername()); }
            catch (ServiceException ex) { return new javafx.beans.property.SimpleStringProperty(String.valueOf(uid)); }
        });
        colMaterial.setCellValueFactory(cell -> {
            int mid = cell.getValue().getMaterialId();
            try { return new javafx.beans.property.SimpleStringProperty(materialService.getMaterial(mid).getName()); }
            catch (ServiceException ex) { return new javafx.beans.property.SimpleStringProperty(String.valueOf(mid)); }
        });
        colQty.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().getQuantity()));
        colNotes.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getNotes() == null ? "" : cell.getValue().getNotes()));
        colStatus.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getStatus()));
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        colRequestedAt.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getRequestedAt() == null ? "" : cell.getValue().getRequestedAt().format(fmt)));

        requestsTable.setItems(data);
    }

    private void setupStatusFilter() {
        statusCombo.setItems(FXCollections.observableArrayList("Svi", "PENDING", "APPROVED", "REJECTED"));
        // default to PENDING to match previous behavior
        statusCombo.getSelectionModel().select("PENDING");
        statusCombo.setOnAction(e -> loadBySelectedStatus());
    }

    private void loadBySelectedStatus() {
        String sel = statusCombo.getSelectionModel().getSelectedItem();
        try {
            List<AssignmentRequest> list;
            if (sel == null || sel.equals("Svi")) {
                list = requestService.listAll();
            } else {
                list = requestService.listByStatus(sel);
            }
            data.setAll(list);
        } catch (ServiceException ex) {
            showError("Greška pri učitavanju", ex.getMessage());
        }
    }

    private AssignmentRequest getSelected() {
        AssignmentRequest sel = requestsTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showWarning("Nije odabran zahtjev", "Molimo odaberite zahtjev iz liste.");
        }
        return sel;
    }

    private void handleApprove() {
        AssignmentRequest sel = getSelected();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potvrdi odobrenje");
        confirm.setHeaderText(null);
        confirm.setContentText("Odobriti zahtjev id=" + sel.getId() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            requestService.approveRequest(sel.getId());
            showInfo("Odobreno", "Zahtjev je odobren i materijal je zadužen.");
            loadBySelectedStatus();
        } catch (ServiceException ex) {
            showError("Greška pri odobrenju", ex.getMessage());
        }
    }

    private void handleReject() {
        AssignmentRequest sel = getSelected();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potvrdi odbijanje");
        confirm.setHeaderText(null);
        confirm.setContentText("Odbiti zahtjev id=" + sel.getId() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            requestService.rejectRequest(sel.getId());
            showInfo("Odbijeno", "Zahtjev je odbijen.");
            loadBySelectedStatus();
        } catch (ServiceException ex) {
            showError("Greška pri odbijanju", ex.getMessage());
        }
    }

    private void showWarning(String title, String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
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
