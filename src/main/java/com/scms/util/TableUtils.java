package com.scms.util;

import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

/**
 * Utility helpers for TableView column sizing.
 *
 * Usage: after setting items on a TableView, call
 * TableUtils.autoResizeColumnsToFitContent(table);
 *
 * This sets the table resize policy to UNCONSTRAINED and computes
 * a reasonable preferred width for each visible column based on
 * header text and a sample of cell values.
 */
public final class TableUtils {

    private TableUtils() {}

    public static void autoResizeColumnsToFitContent(TableView<?> table) {
        if (table == null) return;
        // Allow columns to have widths independent of table width
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        Platform.runLater(() -> {
            try {
                List<?> items = table.getItems();
                int sampleSize = Math.min(items == null ? 0 : items.size(), 200);
                for (TableColumn<?, ?> col : table.getColumns()) {
                    resizeColumnToFitContent(col, items, sampleSize);
                }
                // attach a simple context menu so users can re-run autosize or switch policies
                attachSimpleContextMenu(table);
            } catch (Exception ex) {
                // defensive: don't crash UI if measuring fails
                ex.printStackTrace();
            }
        });
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void resizeColumnToFitContent(TableColumn<?, ?> column, List<?> items, int sampleSize) {
        double padding = 28; // extra padding for cell graphic and padding
        double max = 80; // minimum reasonable width

        // measure header text
        String header = column.getText() == null ? "" : column.getText();
        double headerWidth = computeTextWidth(header, Font.getDefault());
        if (headerWidth > max) max = headerWidth;

        // measure a sample of cell values
        if (items != null && !items.isEmpty()) {
            int limit = Math.min(sampleSize, items.size());
            for (int i = 0; i < limit; i++) {
                Object row = items.get(i);
                try {
                    TableColumn raw = (TableColumn) column;
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
        // clamp to a reasonable max to avoid absurdly wide columns
        double maxAllowed = 1000;
        if (pref > maxAllowed) pref = maxAllowed;
        if (pref < 60) pref = 60;

        column.setPrefWidth(pref);
        column.setResizable(true);
    }

    private static double computeTextWidth(String text, Font font) {
        if (text == null || text.isEmpty()) return 0;
        Text t = new Text(text);
        try {
            t.setFont(font == null ? Font.getDefault() : font);
        } catch (Exception ex) {
            t.setFont(Font.getDefault());
        }
        return t.getLayoutBounds().getWidth();
    }

    private static void attachSimpleContextMenu(TableView<?> table) {
        if (table == null) return;
        Platform.runLater(() -> {
            try {
                ContextMenu menu = new ContextMenu();
                MenuItem autosize = new MenuItem("Auto-size columns");
                autosize.setOnAction(e -> autoResizeColumnsToFitContent(table));
                MenuItem fit = new MenuItem("Fit columns to table width");
                fit.setOnAction(e -> table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY));
                MenuItem unconstrain = new MenuItem("Allow independent column widths");
                unconstrain.setOnAction(e -> table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY));
                menu.getItems().addAll(autosize, fit, unconstrain);
                table.setOnContextMenuRequested(evt -> menu.show(table, evt.getScreenX(), evt.getScreenY()));
            } catch (Exception ignore) {
                // non-critical
            }
        });
    }
}
