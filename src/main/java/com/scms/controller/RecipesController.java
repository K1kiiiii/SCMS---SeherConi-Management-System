package com.scms.controller;

import com.scms.dao.RecipeDao;
import com.scms.model.Recipe;
import com.scms.model.RecipeItem;
import com.scms.service.ServiceException;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for recipes view. Populates recipe cards and provides "Dodijeli radniku" button per recipe.
 */
public class RecipesController {

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
        try {
            List<Recipe> recipes = recipeDao.findAll();
            for (Recipe r : recipes) {
                VBox card = createRecipeCard(r.getId(), r.getName(), r.getDescription());
                recipesPane.getChildren().add(card);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private VBox createRecipeCard(int recipeId, String name, String desc) {
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-weight:bold; -fx-font-size:14px;");
        Label descLbl = new Label(desc != null ? desc : "");
        descLbl.setStyle("-fx-text-fill:#666666;");

        Button assignBtn = new Button("Dodijeli radniku");
        assignBtn.setOnAction(evt -> openAssignDialog(recipeId));

        HBox footer = new HBox(assignBtn);
        footer.setStyle("-fx-alignment: center-right;");

        VBox card = new VBox(6, nameLbl, descLbl, footer);
        card.setStyle("-fx-background-color:#ffffff; -fx-padding:12; -fx-border-radius:6; -fx-background-radius:6;");
        card.setPrefWidth(260);
        return card;
    }

    private void openAssignDialog(int recipeId) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/scms/view/assign_task_dialog.fxml"));
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.setTitle("Dodijeli zadatak");
            javafx.scene.Scene s = new javafx.scene.Scene(loader.load());
            com.scms.controller.AssignTaskController c = loader.getController();
            c.setRecipeId(recipeId);
            dialog.setScene(s);
            dialog.showAndWait();
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
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

        // recipe items area: we'll add rows dynamically using a VBox containing GridPanes per item
        VBox itemsBox = new VBox(8);
        itemsBox.setPadding(new Insets(8,0,8,0));

        Button addItemBtn = new Button("Dodaj sirovinu");
        addItemBtn.setOnAction(evt -> {
            GridPane row = createRecipeItemRow(null);
            itemsBox.getChildren().add(row);
        });

        // start with one empty row
        itemsBox.getChildren().add(createRecipeItemRow(null));

        grid.add(new Label("Sirovine:"), 0, 2);
        grid.add(itemsBox, 1, 2);
        grid.add(addItemBtn, 1, 3);

        // Wrap the content in a ScrollPane so the dialog becomes vertically scrollable
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
        scroll.setFitToWidth(true);
        // reasonable default viewport height so many items will force scrolling
        scroll.setPrefViewportHeight(380);
        dialog.getDialogPane().setContent(scroll);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String name = nameField.getText();
                String desc = descField.getText();

                if (name == null || name.isBlank()) {
                    showWarning("Neispravan unos", "Naziv recepta je obavezan.");
                    return null;
                }

                // gather recipe items
                List<RecipeItem> items = new ArrayList<>();
                for (javafx.scene.Node n : itemsBox.getChildren()) {
                    if (n instanceof GridPane) {
                        GridPane gp = (GridPane) n;
                        TextField materialIdField = (TextField) gp.getUserData();
                        TextField qtyField = (TextField) gp.getChildren().get(1);
                        TextField unitField = (TextField) gp.getChildren().get(3);

                        String mid = materialIdField.getText();
                        String qtxt = qtyField.getText();
                        String unit = unitField.getText();

                        if (mid == null || mid.isBlank()) continue; // skip empty
                        try {
                            int materialId = Integer.parseInt(mid.trim());
                            double qty = Double.parseDouble(qtxt.trim());
                            RecipeItem ri = new RecipeItem();
                            ri.setMaterialId(materialId);
                            ri.setQuantity(qty);
                            ri.setUnit(unit);
                            items.add(ri);
                        } catch (NumberFormatException ex) {
                            showWarning("Neispravan unos", "ID sirovine i količina moraju biti brojevi.");
                            return null;
                        }
                    }
                }

                // create recipe with items
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

        Optional<List<RecipeItem>> res = dialog.showAndWait();
        if (res.isPresent()) {
            // reload recipes
            loadRecipes();
            showInfo("Uspjeh", "Recept je dodan.");
        }
    }

    private GridPane createRecipeItemRow(RecipeItem existing) {
        GridPane row = new GridPane();
        row.setHgap(8);
        row.setVgap(4);

        TextField materialIdField = new TextField();
        materialIdField.setPromptText("materialId");
        TextField qtyField = new TextField();
        qtyField.setPromptText("količina");
        TextField unitField = new TextField();
        unitField.setPromptText("jed.");

        Button remove = new Button("Ukloni");
        remove.setOnAction(evt -> ((VBox)row.getParent()).getChildren().remove(row));

        row.add(new Label("ID:"), 0, 0);
        row.add(materialIdField, 1, 0);
        row.add(qtyField, 2, 0);
        row.add(unitField, 3, 0);
        row.add(remove, 4, 0);

        // store ref to first field for easy extraction
        row.setUserData(materialIdField);
        return row;
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
