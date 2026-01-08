package com.scms.util;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

public final class DialogUtils {

    private DialogUtils() {}

    public static void styleDialog(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        // ensure app stylesheet is present on the dialog pane
        try {
            String css = DialogUtils.class.getResource("/com/scms/css/light-theme.css").toExternalForm();
            if (!pane.getStylesheets().contains(css)) pane.getStylesheets().add(css);
        } catch (Exception ignored) {}
        // add the app root style so our .border-pane rules apply
        if (!pane.getStyleClass().contains("border-pane")) pane.getStyleClass().add("border-pane");

        // Tag dialog buttons for more specific styling
        for (ButtonType bt : pane.getButtonTypes()) {
            Button b = (Button) pane.lookupButton(bt);
            if (b == null) continue;
            if (!b.getStyleClass().contains("dialog-button")) b.getStyleClass().add("dialog-button");
            ButtonBar.ButtonData data = bt.getButtonData();
            if (data == ButtonBar.ButtonData.OK_DONE || data == ButtonBar.ButtonData.YES || data == ButtonBar.ButtonData.APPLY) {
                if (!b.getStyleClass().contains("primary")) b.getStyleClass().add("primary");
            } else {
                if (!b.getStyleClass().contains("secondary")) b.getStyleClass().add("secondary");
            }
        }
    }

    public static void styleAlert(javafx.scene.control.Alert alert) {
        styleDialog(alert);
    }
}

