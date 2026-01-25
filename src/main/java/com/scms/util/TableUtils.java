package com.scms.util;

import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility helpers for TableView column sizing.
 */
public final class TableUtils {

    private static final Logger LOGGER = Logger.getLogger(TableUtils.class.getName());

    private TableUtils() {}

    public static void autoResizeColumnsToFitContent(TableView<?> table) {
        if (table == null) return;
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        Platform.runLater(() -> {
            try {
                List<?> items = table.getItems();
                int sampleSize = Math.min(items == null ? 0 : items.size(), 200);
                for (TableColumn<?, ?> col : table.getColumns()) {
                    resizeColumnToFitContent(col, items, sampleSize);
                }
                attachSimpleContextMenu(table);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error in auto-resizing columns", ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void resizeColumnToFitContent(TableColumn<?, ?> column, List<?> items, int sampleSize) {
        double padding = 28;
        double max = 80;

        String header = column.getText() == null ? "" : column.getText();
        double headerWidth = computeTextWidth(header, Font.getDefault());
        if (headerWidth > max) max = headerWidth;

        if (items != null && !items.isEmpty()) {
            int limit = Math.min(sampleSize, items.size());
            for (int i = 0; i < limit; i++) {
                Object row = items.get(i);
                try {
                    // Use a typed reference so getCellData accepts Object parameter for static analysis
                    TableColumn<Object, Object> raw = (TableColumn<Object, Object>) column;
                    Object cellValue = raw.getCellData(row);
                    String s = cellValue == null ? "" : cellValue.toString();
                    double w = computeTextWidth(s, Font.getDefault());
                    if (w > max) max = w;
                } catch (Exception ignore) {
                    // some columns may throw if value mapping depends on state; ignore
                }
            }
        }

        double pref = Math.ceil(max + padding);
        double maxAllowed = 1000;
        if (pref > maxAllowed) pref = maxAllowed;
        if (pref < 60) pref = 60;

        column.setPrefWidth(pref);
        column.setResizable(true);
    }

    private static double computeTextWidth(String text, Font font) {
        if (text == null || text.isEmpty()) return 0;
        Text t = new Text(text);
        try { t.setFont(font == null ? Font.getDefault() : font); } catch (Exception ex) { t.setFont(Font.getDefault()); }
        return t.getLayoutBounds().getWidth();
    }

    private static void attachSimpleContextMenu(TableView<?> table) {
        if (table == null) return;
        Platform.runLater(() -> {
            try {
                ContextMenu menu = new ContextMenu();
                MenuItem autosize = new MenuItem("Auto-size columns");
                autosize.setOnAction(e -> { autoResizeColumnsToFitContent(table); if (e != null) e.consume(); });
                MenuItem fit = new MenuItem("Fit columns to table width");
                // CONSTRAINED_RESIZE_POLICY is deprecated but acceptable for user convenience here
                @SuppressWarnings("deprecation")
                final javafx.event.EventHandler<javafx.event.ActionEvent> fitHandler = e -> { table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); if (e != null) e.consume(); };
                fit.setOnAction(fitHandler);
                MenuItem unconstrain = new MenuItem("Allow independent column widths");
                unconstrain.setOnAction(e -> { table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY); if (e != null) e.consume(); });
                menu.getItems().addAll(autosize, fit, unconstrain);
                table.setOnContextMenuRequested(evt -> menu.show(table, evt.getScreenX(), evt.getScreenY()));
            } catch (Exception ignore) {
                // non-critical
            }
        });
    }

    @SuppressWarnings("unused")
    public static HBox createColumnActionsMenu(TableView<?> table) {
        HBox h = new HBox();
        try {
            MenuButton menu = new MenuButton("Columns");
            for (TableColumn<?, ?> column : table.getColumns()) {
                CheckMenuItem item = new CheckMenuItem(column.getText());
                item.setSelected(column.isVisible());
                item.selectedProperty().addListener((obs, oldV, newV) -> column.setVisible(newV));
                menu.getItems().add(item);
            }
            h.getChildren().add(menu);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to create column actions menu", ex);
        }
        return h;
    }
}
