package com.scms.controller;

import com.scms.dao.MaterialDao;
import com.scms.dao.RecipeDao;
import com.scms.model.Recipe;
import com.scms.model.RecipeItem;
import com.scms.util.DialogUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
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
                try { matName = md.findById(ri.getMaterialId()).map(m -> m.getName()).orElse(matName); } catch (SQLException ignored) {}
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
