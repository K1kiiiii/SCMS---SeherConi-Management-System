package com.scms.controller;

import com.scms.dao.MaterialDao;
import com.scms.dao.RecipeDao;
import com.scms.model.Recipe;
import com.scms.model.RecipeItem;
import com.scms.model.Material;
import com.scms.util.DialogUtils;
import com.scms.util.RoleManager;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class RecipeDetailDialog {

    public static void show(int recipeId) {
        RecipeDao rd = new RecipeDao();
        MaterialDao md = new MaterialDao();
        try {
            Optional<Recipe> maybe = rd.findById(recipeId);
            if (maybe.isEmpty()) {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                DialogUtils.styleAlert(a);
                a.setTitle("Recept nije pronađen");
                a.setHeaderText(null);
                a.setContentText("Ne mogu pronaći recept.");
                a.showAndWait();
                return;
            }

            Recipe r = maybe.get();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Detalji recepta — " + r.getName());

            Label title = new Label(r.getName());
            title.getStyleClass().add("dialog-title");

            Label desc = new Label(r.getDescription() != null ? r.getDescription() : "");
            desc.setWrapText(true);
            desc.getStyleClass().add("dialog-subtitle");

            VBox content = new VBox(8);
            content.setPadding(new Insets(16));
            content.getChildren().addAll(title, desc);

            List<RecipeItem> items = r.getItems();
            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(8);
            grid.setPadding(new Insets(8,0,8,0));

            int row = 0;
            for (RecipeItem ri : items) {
                String matName = "#" + ri.getMaterialId();
                try { matName = md.findById(ri.getMaterialId()).map(Material::getName).orElse(matName); } catch (SQLException ignored) {}
                Label nameLbl = new Label(matName);
                nameLbl.getStyleClass().add("recipe-item-name");
                Label qtyLbl = new Label(String.format("%.2f %s", ri.getQuantity(), ri.getUnit() != null ? ri.getUnit() : ""));
                qtyLbl.getStyleClass().add("recipe-item-qty");

                grid.add(nameLbl, 0, row);
                grid.add(qtyLbl, 1, row);
                row++;
            }

            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(220);

            VBox.setVgrow(scroll, Priority.ALWAYS);
            content.getChildren().add(scroll);

            // Button bar: Close always present; Edit visible only to ADMIN
            HBox buttonBar = new HBox(8);
            buttonBar.setPadding(new Insets(8,0,0,0));

            Button closeBtn = new Button("Zatvori");
            closeBtn.getStyleClass().addAll("secondary-button");
            closeBtn.setOnAction(evt -> { evt.consume(); dialog.close(); });

            buttonBar.getChildren().add(closeBtn);

            if (RoleManager.isAdmin()) {
                Button editBtn = new Button("Uredi");
                editBtn.getStyleClass().addAll("primary-button");
                editBtn.setOnAction(evt -> { evt.consume();
                    // Open a simple edit dialog for name/description
                    Dialog<Recipe> edit = new Dialog<>();
                    edit.setTitle("Uredi recept");
                    edit.setHeaderText("Uredi naziv i opis recepta");

                    ButtonType saveBtnType = new ButtonType("Snimi", ButtonBar.ButtonData.OK_DONE);
                    edit.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

                    GridPane g = new GridPane();
                    g.setHgap(8);
                    g.setVgap(8);
                    g.setPadding(new Insets(12));

                    TextField nameField = new TextField(r.getName());
                    TextArea descField = new TextArea(r.getDescription() == null ? "" : r.getDescription());
                    descField.setPrefRowCount(4);

                    g.add(new Label("Naziv:"), 0, 0);
                    g.add(nameField, 1, 0);
                    g.add(new Label("Opis:"), 0, 1);
                    g.add(descField, 1, 1);

                    edit.getDialogPane().setContent(g);
                    DialogUtils.styleDialog(edit);

                    edit.setResultConverter(btn -> {
                        if (btn == saveBtnType) {
                            String newName = nameField.getText();
                            String newDesc = descField.getText();
                            if (newName == null || newName.isBlank()) {
                                javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                                DialogUtils.styleAlert(a);
                                a.setTitle("Neispravan unos");
                                a.setHeaderText(null);
                                a.setContentText("Naziv recepta ne smije biti prazan.");
                                a.showAndWait();
                                return null;
                            }
                            Recipe updated = new Recipe();
                            updated.setId(r.getId());
                            updated.setName(newName.trim());
                            updated.setDescription(newDesc == null ? "" : newDesc.trim());
                            return updated;
                        }
                        return null;
                    });

                    Optional<Recipe> res = edit.showAndWait();
                    if (res.isPresent()) {
                        Recipe toSave = res.get();
                        try {
                            Optional<Recipe> saved = rd.update(toSave);
                            if (saved.isPresent()) {
                                Recipe s = saved.get();
                                title.setText(s.getName());
                                desc.setText(s.getDescription() == null ? "" : s.getDescription());
                                // notify user
                                Alert info = new Alert(Alert.AlertType.INFORMATION);
                                DialogUtils.styleAlert(info);
                                info.setTitle("Uspjeh");
                                info.setHeaderText(null);
                                info.setContentText("Recept ažuriran.");
                                info.showAndWait();
                            } else {
                                Alert err = new Alert(Alert.AlertType.ERROR);
                                DialogUtils.styleAlert(err);
                                err.setTitle("Greška");
                                err.setHeaderText(null);
                                err.setContentText("Ne mogu ažurirati recept.");
                                err.showAndWait();
                            }
                        } catch (SQLException ex) {
                            Alert err = new Alert(Alert.AlertType.ERROR);
                            DialogUtils.styleAlert(err);
                            err.setTitle("Greška");
                            err.setHeaderText(null);
                            err.setContentText("Greška pri spremanju: " + ex.getMessage());
                            err.showAndWait();
                        }
                    }
                });
                // place edit button to the left of close (primary on right visually)
                // add edit button (before close) - use add(editBtn) to avoid IDE suggestion warnings
                buttonBar.getChildren().add(editBtn);
            }

            content.getChildren().add(buttonBar);

            Scene scene = new Scene(content, 520, 360);
            // apply main app stylesheet to the scene root safely
            try {
                java.net.URL cssUrl = RecipeDetailDialog.class.getResource("/com/scms/css/light-theme.css");
                if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            } catch (Exception ignored) {}

            // add a root style class so dialog content follows app theming
            if (!content.getStyleClass().contains("border-pane")) content.getStyleClass().add("border-pane");

            dialog.setScene(scene);
            // do not call DialogUtils.styleDialog on a Stage; style Dialogs only
            dialog.showAndWait();

        } catch (SQLException ex) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            DialogUtils.styleAlert(a);
            a.setTitle("Greška");
            a.setHeaderText(null);
            a.setContentText("Ne mogu učitati recept: " + ex.getMessage());
            a.showAndWait();
        }
    }
}
