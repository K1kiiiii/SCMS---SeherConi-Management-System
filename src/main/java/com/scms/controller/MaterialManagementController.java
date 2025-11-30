package com.scms.controller;

import com.scms.model.Material;
import com.scms.service.MaterialService;
import com.scms.service.ServiceException;
import com.scms.util.RoleManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class MaterialManagementController {

    @FXML private TableView<Material> materialTable;

    @FXML private TableColumn<Material, Integer>       colId;
    @FXML private TableColumn<Material, String>        colName;
    @FXML private TableColumn<Material, Double>        colQuantity;
    @FXML private TableColumn<Material, String>        colUnit;
    @FXML private TableColumn<Material, String>        colSupplier;
    @FXML private TableColumn<Material, LocalDateTime> colUpdatedAt;

    @FXML private TextField       searchField;
    @FXML private ComboBox<String> supplierFilter;

    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    private final MaterialService materialService = new MaterialService();
    private ObservableList<Material> allMaterials = FXCollections.observableArrayList();
    private FilteredList<Material>   filteredMaterials;

    private final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        setupTableColumns();
        loadMaterials();
        setupFilters();
        applyRolePermissions();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unit"));
        colSupplier.setCellValueFactory(new PropertyValueFactory<>("supplier"));
        colUpdatedAt.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));

        colUpdatedAt.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(DATE_TIME_FORMATTER.format(item));
                }
            }
        });
    }

    private void loadMaterials() {
        try {
            List<Material> fromDb = materialService.listMaterials();
            allMaterials = FXCollections.observableArrayList(fromDb);
            filteredMaterials = new FilteredList<>(allMaterials, m -> true);
            materialTable.setItems(filteredMaterials);
        } catch (ServiceException ex) {
            showError("Greška pri učitavanju materijala", ex.getMessage());
        }
    }

    private void setupFilters() {
        supplierFilter.getItems().clear();
        supplierFilter.getItems().add("Svi dobavljači");

        allMaterials.stream()
                .map(Material::getSupplier)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .forEach(supplierFilter.getItems()::add);

        supplierFilter.getSelectionModel().selectFirst();
        applyFilterPredicate();
    }

    private void applyRolePermissions() {
        // Admin + Magacioner mogu uređivati; Radnik je read-only
        boolean canEdit = RoleManager.isAdmin() || RoleManager.isMagacioner();
        addButton.setDisable(!canEdit);
        editButton.setDisable(!canEdit);
        deleteButton.setDisable(!canEdit);
    }

    @FXML
    private void handleSearch() {
        applyFilterPredicate();
    }

    @FXML
    private void handleFilterChanged() {
        applyFilterPredicate();
    }

    @FXML
    private void handleClearFilters(ActionEvent event) {
        searchField.clear();
        supplierFilter.getSelectionModel().selectFirst();
        applyFilterPredicate();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadMaterials();
        setupFilters();
    }

    private void applyFilterPredicate() {
        if (filteredMaterials == null) return;

        String searchText = searchField.getText() != null ? searchField.getText().toLowerCase() : "";
        String supplier   = supplierFilter.getSelectionModel().getSelectedItem();

        filteredMaterials.setPredicate(material -> {
            if (material == null) return false;

            boolean matchesSearch = searchText.isEmpty()
                    || (material.getName() != null
                    && material.getName().toLowerCase().contains(searchText))
                    || (material.getSupplier() != null
                    && material.getSupplier().toLowerCase().contains(searchText));

            boolean matchesSupplier = (supplier == null
                    || supplier.equals("Svi dobavljači"))
                    || (material.getSupplier() != null
                    && supplier.equalsIgnoreCase(material.getSupplier()));

            return matchesSearch && matchesSupplier;
        });
    }

    @FXML
    private void handleAddMaterial(ActionEvent event) {
        Material newMaterial = showMaterialDialog(null);
        if (newMaterial != null) {
            try {
                if (newMaterial.getUpdatedAt() == null) {
                    newMaterial.setUpdatedAt(LocalDateTime.now());
                }
                Material created = materialService.createMaterial(newMaterial);
                if (created != null) {
                    allMaterials.add(created);
                    setupFilters();
                } else {
                    loadMaterials();
                    setupFilters();
                }
            } catch (ServiceException ex) {
                showError("Greška pri kreiranju materijala", ex.getMessage());
            }
        }
    }

    @FXML
    private void handleEditMaterial(ActionEvent event) {
        Material selected = materialTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Nije odabran materijal",
                    "Molimo odaberite materijal koji želite urediti.");
            return;
        }

        Material updated = showMaterialDialog(selected);
        if (updated != null) {
            try {
                if (updated.getUpdatedAt() == null) {
                    updated.setUpdatedAt(LocalDateTime.now());
                }
                materialService.updateMaterial(updated);
                materialTable.refresh();
                setupFilters();
            } catch (ServiceException ex) {
                showError("Greška pri ažuriranju materijala", ex.getMessage());
            }
        }
    }

    @FXML
    private void handleDeleteMaterial(ActionEvent event) {
        Material selected = materialTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Nije odabran materijal",
                    "Molimo odaberite materijal koji želite obrisati.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Brisanje materijala");
        confirm.setHeaderText("Da li ste sigurni da želite obrisati odabrani materijal?");
        confirm.setContentText(selected.getName());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                materialService.deleteMaterial(selected.getId());
                allMaterials.remove(selected);
                setupFilters();
            } catch (ServiceException ex) {
                showError("Greška pri brisanju materijala", ex.getMessage());
            }
        }
    }



    private Material showMaterialDialog(Material material) {
        boolean editMode = (material != null);

        Dialog<Material> dialog = new Dialog<>();
        dialog.setTitle(editMode ? "Uredi materijal" : "Novi materijal");
        dialog.setHeaderText(editMode ? "Uredi postojeći materijal"
                : "Unesite podatke o materijalu");

        ButtonType saveButtonType = new ButtonType("Snimi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField     = new TextField();
        TextField quantityField = new TextField();
        TextField unitField     = new TextField();
        TextField supplierField = new TextField();

        if (editMode) {
            nameField.setText(material.getName());
            quantityField.setText(String.valueOf(material.getQuantity()));
            unitField.setText(material.getUnit());
            supplierField.setText(material.getSupplier());
        }

        grid.add(new Label("Naziv:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Količina:"), 0, 1);
        grid.add(quantityField, 1, 1);

        grid.add(new Label("Jedinica:"), 0, 2);
        grid.add(unitField, 1, 2);

        grid.add(new Label("Dobavljač:"), 0, 3);
        grid.add(supplierField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    double quantity = Double.parseDouble(quantityField.getText());

                    if (editMode) {
                        material.setName(nameField.getText());
                        material.setQuantity(quantity);
                        material.setUnit(unitField.getText());
                        material.setSupplier(supplierField.getText());
                        return material;
                    } else {
                        Material m = new Material();
                        m.setName(nameField.getText());
                        m.setQuantity(quantity);
                        m.setUnit(unitField.getText());
                        m.setSupplier(supplierField.getText());
                        // id + updatedAt će riješiti DB / service
                        return m;
                    }
                } catch (NumberFormatException ex) {
                    showWarning("Neispravan unos",
                            "Količina mora biti broj.");
                    return null;
                }
            }
            return null;
        });

        Optional<Material> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
