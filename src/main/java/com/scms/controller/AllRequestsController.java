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

import java.time.format.DateTimeFormatter;
import java.util.List;

public class AllRequestsController {

    @FXML private TableView<AssignmentRequest> requestsTable;
    @FXML private TableColumn<AssignmentRequest, Integer> colId;
    @FXML private TableColumn<AssignmentRequest, String> colUser;
    @FXML private TableColumn<AssignmentRequest, String> colMaterial;
    @FXML private TableColumn<AssignmentRequest, Double> colQty;
    @FXML private TableColumn<AssignmentRequest, String> colNotes;
    @FXML private TableColumn<AssignmentRequest, String> colStatus;
    @FXML private TableColumn<AssignmentRequest, String> colRequestedAt;
    @FXML private Button refreshBtn;

    private final AssignmentRequestService requestService = new AssignmentRequestService();
    private final UserService userService = new UserService();
    private final MaterialService materialService = new MaterialService();

    private final ObservableList<AssignmentRequest> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        loadAll();
        refreshBtn.setOnAction(e -> loadAll());
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
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        colRequestedAt.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getRequestedAt() == null ? "" : cell.getValue().getRequestedAt().format(fmt)));

        requestsTable.setItems(data);
    }

    private void loadAll() {
        try {
            List<AssignmentRequest> list = requestService.listAll();
            data.setAll(list);
        } catch (ServiceException ex) {
            showError("Greška pri učitavanju", ex.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}

