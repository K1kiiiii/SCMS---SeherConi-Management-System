package com.scms.controller;

import com.scms.dao.RecipeDao;
import com.scms.model.Recipe;
import com.scms.model.RecipeItem;
import com.scms.service.ServiceException;
import com.scms.util.DialogUtils;
import com.scms.util.LoadingOverlay;
import com.scms.util.InputSanitizer;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.scms.service.MaterialService;
import com.scms.model.Material;

import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class RecipesController {

    private static final Logger LOGGER = Logger.getLogger(RecipesController.class.getName());

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private FlowPane recipesPane;
    @FXML private Button addButton;

    private final RecipeDao recipeDao = new RecipeDao();

    @FXML
    public void initialize() {
        loadRecipes();
    }

    private void loadRecipes() {
        recipesPane.getChildren().clear();
        // show overlay while loading recipes
        LoadingOverlay.show(recipesPane);
        try {
            List<Recipe> recipes = recipeDao.findAll();
            for (Recipe r : recipes) {
                VBox card = createRecipeCard(r.getId(), r.getName(), r.getDescription());
                recipesPane.getChildren().add(card);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load recipes", ex);
        } finally {
            LoadingOverlay.hide(recipesPane);
        }
    }

    private VBox createRecipeCard(int recipeId, String name, String desc) {
        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("recipe-name");

        Label descLbl = new Label(desc != null ? desc : "");
        descLbl.getStyleClass().add("recipes-subtitle");

        Button assignBtn = new Button("Dodijeli radniku");
        assignBtn.getStyleClass().add("primary-button");
        assignBtn.setOnAction(evt -> openAssignDialog(recipeId));

        HBox footer = new HBox(assignBtn);
        footer.setStyle("-fx-alignment: center-right;");

        VBox card = new VBox(6, nameLbl, descLbl, footer);
        card.getStyleClass().add("task-card");
        card.setPrefWidth(260);

        card.setOnMouseClicked(ev -> RecipeDetailDialog.show(recipeId));
        card.setStyle(card.getStyle() + " -fx-cursor: hand;");
        assignBtn.setOnMouseClicked(evt -> { evt.consume(); openAssignDialog(recipeId); });

        return card;
    }

    private void openAssignDialog(int recipeId) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/scms/view/assign_task_dialog.fxml"));
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.setTitle("Dodijeli zadatak");
            javafx.scene.Scene s = new javafx.scene.Scene(loader.load());
            // ensure app stylesheet is applied so the dialog matches main app theme
            try {
                java.net.URL css = getClass().getResource("/com/scms/css/light-theme.css");
                if (css != null) s.getStylesheets().add(css.toExternalForm());
            } catch (Exception ignored) {}
            com.scms.controller.AssignTaskController c = loader.getController();
            c.setRecipeId(recipeId);
            dialog.setScene(s);
            dialog.showAndWait();
        } catch (java.io.IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to open assign dialog", ex);
        }
    }

    @FXML
    private void handleAddRecipe() {
        Dialog<List<RecipeItem>> dialog = new Dialog<>();
        dialog.setTitle("Novi recept");
        dialog.setHeaderText("Unesite podatke o receptu");

        ButtonType saveButtonType = new ButtonType("Snimi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        TextArea descField = new TextArea();
        descField.setPrefRowCount(3);

        grid.add(new Label("Naziv recepta:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Opis:"), 0, 1);
        grid.add(descField, 1, 1);

        VBox itemsBox = new VBox(8);
        itemsBox.setPadding(new Insets(8,0,8,0));

        Button addItemBtn = new Button("Dodaj sirovinu");
        addItemBtn.setOnAction(evt -> {
            if (evt != null) evt.consume();
            GridPane row = createRecipeItemRow();
            itemsBox.getChildren().add(row);
        });

        itemsBox.getChildren().add(createRecipeItemRow());

        grid.add(new Label("Sirovine:"), 0, 2);
        grid.add(itemsBox, 1, 2);
        grid.add(addItemBtn, 1, 3);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(380);
        dialog.getDialogPane().setContent(scroll);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String name = InputSanitizer.sanitizeText(nameField.getText());
                String desc = InputSanitizer.sanitizeMultiline(descField.getText(), 4000);

                if (name == null || name.isBlank()) {
                    showWarning("Neispravan unos", "Naziv recepta je obavezan.");
                    return null;
                }

                List<RecipeItem> items = new ArrayList<>();
                for (javafx.scene.Node n : itemsBox.getChildren()) {
                    if (n instanceof GridPane gp) {
                        Object ud = gp.getUserData();
                        @SuppressWarnings("unchecked")
                        ComboBox<Material> materialCombo = (ud instanceof ComboBox) ? (ComboBox<Material>) ud : null;
                        // children order: 0=Label,1=materialCombo,2=qtyField,3=unitField,4=remove
                        TextField qtyField = (TextField) gp.getChildren().get(2);
                        TextField unitField = (TextField) gp.getChildren().get(3);

                        Material material = materialCombo == null ? null : materialCombo.getValue();
                        String unit = InputSanitizer.sanitizeText(unitField.getText());
                        double qty = 0.0;
                        if (qtyField.getText() != null && !qtyField.getText().isBlank()) {
                            Double parsed = InputSanitizer.parseDoubleOrNull(qtyField.getText().trim());
                            if (parsed == null) {
                                showWarning("Neispravan unos", "Količina mora biti broj.");
                                return null;
                            }
                            qty = parsed;
                        }

                        if (material == null) {
                            showWarning("Neispravan unos", "Sirovina je obavezna.");
                            return null;
                        }

                        RecipeItem ri = new RecipeItem();
                        ri.setMaterialId(material.getId());
                        ri.setQuantity(qty);
                        ri.setUnit(unit);
                        items.add(ri);
                    }
                }

                try {
                    Recipe r = new Recipe();
                    r.setName(name);
                    r.setDescription(desc);
                    Recipe created = recipeDao.createWithItems(r, items);
                    // update UI by returning items as result
                    return items;
                } catch (SQLException ex) {
                    showWarning("Greška", "Ne mogu spremiti recept: " + ex.getMessage());
                }
            }
            return null;
        });

        DialogUtils.styleDialog(dialog);
        Optional<List<RecipeItem>> res = dialog.showAndWait();
        if (res.isPresent()) {
            loadRecipes();
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            DialogUtils.styleAlert(info);
            info.setTitle("Uspjeh");
            info.setHeaderText(null);
            info.setContentText("Recept je dodan.");
            info.showAndWait();
        }
    }

    private GridPane createRecipeItemRow() {
        GridPane row = new GridPane();
        row.setHgap(8);
        row.setVgap(4);

        // choose material by name instead of entering ID
        MaterialService materialService = new MaterialService();
        ComboBox<Material> materialCombo = new ComboBox<>();
        try {
            List<Material> mats = materialService.listMaterials();
            materialCombo.setItems(javafx.collections.FXCollections.observableArrayList(mats));
        } catch (ServiceException ex) {
        }
        materialCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Material item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.getName()); }
        });
        materialCombo.setButtonCell(new javafx.scene.control.ListCell<>() { @Override protected void updateItem(Material item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.getName()); } });
        materialCombo.setPromptText("Sirovina");
        TextField qtyField = new TextField();
        qtyField.setPromptText("količina");
        TextField unitField = new TextField();
        unitField.setPromptText("jed.");

        Button remove = new Button("Ukloni");
        remove.setOnAction(evt -> { if (evt != null) evt.consume(); ((VBox)row.getParent()).getChildren().remove(row); });

        row.add(new Label("Sirovina:"), 0, 0);
        row.add(materialCombo, 1, 0);
        row.add(qtyField, 2, 0);
        row.add(unitField, 3, 0);
        row.add(remove, 4, 0);

        row.setUserData(materialCombo);
        return row;
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        DialogUtils.styleAlert(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        DialogUtils.styleAlert(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
