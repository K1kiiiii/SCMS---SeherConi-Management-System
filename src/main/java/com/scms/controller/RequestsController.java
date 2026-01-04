package com.scms.controller;

import com.scms.model.Assignment;
import com.scms.model.Material;
import com.scms.model.User;
import com.scms.service.AssignmentService;
import com.scms.service.MaterialService;
import com.scms.service.ServiceException;
import com.scms.service.UserService;
import com.scms.util.RoleManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestsController {

    @FXML private TableView<Assignment> requestsTable;
    @FXML private TableColumn<Assignment, Integer> colId;
    @FXML private TableColumn<Assignment, String> colUser;
    @FXML private TableColumn<Assignment, String> colMaterial;
    @FXML private TableColumn<Assignment, Double> colQuantity;
    @FXML private TableColumn<Assignment, String> colStatus;
    @FXML private TableColumn<Assignment, String> colNotes;
    @FXML private TableColumn<Assignment, LocalDateTime> colAssignedAt;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;

    private final AssignmentService assignmentService = new AssignmentService();
    private final UserService userService = new UserService();
    private final MaterialService materialService = new MaterialService();
    private ObservableList<Assignment> allRequests = FXCollections.observableArrayList();
    private FilteredList<Assignment> filteredRequests;
    private final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // caches to avoid per-cell DB calls
    private final Map<Integer, String> userNameMap = new HashMap<>();
    private final Map<Integer, String> materialNameMap = new HashMap<>();


    @FXML
    public void initialize() {
        setupColumns();
        statusFilter.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> handleFilterChanged());
        loadRequests();
        applyRolePermissions();
    }

    private void applyRolePermissions() {
        boolean canManage = RoleManager.isAdmin() || RoleManager.isMagacioner();
        approveButton.setDisable(!canManage);
        rejectButton.setDisable(!canManage);
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));
        colAssignedAt.setCellValueFactory(new PropertyValueFactory<>("assignedAt"));

        // Use cached maps to avoid hitting services per cell render
        colUser.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    Assignment a = (Assignment) getTableRow().getItem();
                    String name = userNameMap.get(a.getUserId());
                    if (name != null) setText(name);
                    else setText(String.valueOf(a.getUserId()));
                }
            }
        });

        colMaterial.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    Assignment a = (Assignment) getTableRow().getItem();
                    String name = materialNameMap.get(a.getMaterialId());
                    if (name != null) setText(name);
                    else setText(String.valueOf(a.getMaterialId()));
                }
            }
        });

        colAssignedAt.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(DATE_TIME_FORMATTER.format(item));
            }
        });
    }

    private void loadRequests() {
        // Run DB/service work off the FX thread
        Task<List<Assignment>> task = new Task<>() {
            @Override
            protected List<Assignment> call() throws Exception {
                List<Assignment> list;
                if (RoleManager.isAdmin() || RoleManager.isMagacioner()) {
                    list = assignmentService.listAll();
                } else {
                    User current = RoleManager.getLoggedInUser();
                    list = assignmentService.listAssignmentsForUser(current.getId());
                }

                // populate caches
                try {
                    List<User> users = userService.getAllUsers();
                    userNameMap.clear();
                    for (User u : users) userNameMap.put(u.getId(), u.getUsername());
                } catch (ServiceException ex) {
                    // ignore here; UI will still show ids
                }

                try {
                    List<Material> materials = materialService.listMaterials();
                    materialNameMap.clear();
                    for (Material m : materials) materialNameMap.put(m.getId(), m.getName());
                } catch (ServiceException ex) {
                    // ignore here; UI will still show ids
                }

                return list;
            }
        };

        task.setOnSucceeded(e -> {
            List<Assignment> list = task.getValue();
            allRequests = FXCollections.observableArrayList(list);
            filteredRequests = new FilteredList<>(allRequests, r -> true);
            requestsTable.setItems(filteredRequests);
            Set<String> statuses = allRequests.stream()
                    .map(a -> a.getStatus() != null ? a.getStatus() : "")
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            statusFilter.getItems().clear();
            statusFilter.getItems().add("Svi");
            statuses.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(statusFilter.getItems()::add);
            statusFilter.getSelectionModel().selectFirst();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            showError("Greška pri učitavanju zahtjeva", ex == null ? "Unknown error" : ex.getMessage());
        });

        Thread th = new Thread(task, "requests-loader");
        th.setDaemon(true);
        th.start();
    }

    @FXML
    private void handleRefresh() {
        loadRequests();
    }

    @FXML
    private void handleFilterChanged() {
        if (filteredRequests == null) return;
        String sel = statusFilter.getSelectionModel().getSelectedItem();
        if (sel == null || sel.equals("Svi")) {
            filteredRequests.setPredicate(r -> true);
        } else {
            filteredRequests.setPredicate(r -> sel.equalsIgnoreCase(r.getStatus()));
        }
    }

    @FXML
    private void handleApprove() {
        Assignment sel = requestsTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Nije odabran zahtjev", "Molimo odaberite zahtjev za odobravanje.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potvrda odobravanja");
        confirm.setHeaderText("Odobriti zahtjev?");
        confirm.setContentText("Zahtjev ID=" + sel.getId() + ", sirovina ID=" + sel.getMaterialId());

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            try {
                assignmentService.approveRequest(sel.getId());
                loadRequests();
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Odobreno");
                info.setHeaderText(null);
                info.setContentText("Zahtjev je uspješno odobren.");
                info.showAndWait();
            } catch (ServiceException ex) {
                showError("Greška pri odobravanju", ex.getMessage());
            }
        }
    }

    @FXML
    private void handleReject() {
        Assignment sel = requestsTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Nije odabran zahtjev", "Molimo odaberite zahtjev za odbijanje.");
            return;
        }
        TextInputDialog input = new TextInputDialog();
        input.setTitle("Razlog odbijanja");
        input.setHeaderText("Unesite razlog za odbijanje (opcionalno):");
        input.setContentText("Razlog:");

        Optional<String> maybeReason = input.showAndWait();
        if (maybeReason.isPresent()) {
            try {
                assignmentService.rejectRequest(sel.getId(), maybeReason.get());
                loadRequests();
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Odbijeno");
                info.setHeaderText(null);
                info.setContentText("Zahtjev je odbijen.");
                info.showAndWait();
            } catch (ServiceException ex) {
                showError("Greška pri odbijanju", ex.getMessage());
            }
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
