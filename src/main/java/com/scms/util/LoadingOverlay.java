package com.scms.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.layout.Pane;

/**
 * Simple utility to show/hide a loading overlay inside the application's content area (StackPane with id #contentArea).
 * Usage: LoadingOverlay.show(anyNodeInScene); LoadingOverlay.hide(anyNodeInScene);
 */
public final class LoadingOverlay {
    private static final String OVERLAY_ID = "loading-overlay";
    // Note: no Stage fallback — overlay will only be attached to content-area panes
    // Track nodes that requested an overlay before they were attached to a Scene
    private static final Set<Node> pendingShows = Collections.newSetFromMap(new WeakHashMap<>());
    // Track nodes for which hide() was invoked before the overlay was created
    private static final Set<Node> canceledShows = Collections.newSetFromMap(new WeakHashMap<>());
    private LoadingOverlay() {}

    public static void show(Node anyNode) {
        if (anyNode == null) return;
        Runnable showAction = () -> {
            Scene s = anyNode.getScene();
            // If node not yet attached, remember the show request and show when attached
            if (s == null || s.getRoot() == null) {
                pendingShows.add(anyNode);
                anyNode.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        // remove from pending set (we will process it now)
                        pendingShows.remove(anyNode);
                        // if a hide() arrived before the node attached, cancel the show
                        if (canceledShows.remove(anyNode)) return;
                        // schedule on FX thread
                        Platform.runLater(() -> show(anyNode));
                    }
                });
                return;
            }

            // Prefer adding overlay to #contentArea if present.
            // If not present yet, retry a few times (the dashboard may be wiring contentArea later).
            Node contentArea = s.getRoot().lookup("#contentArea");
            if (contentArea == null) {
                // retry up to 20 times with short delays; if still not found, give up (do not attach overlay to root)
                AtomicBoolean scheduled = new AtomicBoolean(false);
                new Thread(() -> {
                    for (int i = 0; i < 20 && !scheduled.get(); i++) {
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            Scene ss = anyNode.getScene();
                            if (ss == null || ss.getRoot() == null) return;
                            Node ca = ss.getRoot().lookup("#contentArea");
                            if (ca != null) {
                                scheduled.set(true);
                                // call show again now that contentArea exists
                                show(anyNode);
                            }
                        });
                    }
                }, "overlay-contentarea-retry").start();
                // return now; the retry thread will call show() again when contentArea appears
                return;
            }

            if (contentArea instanceof Pane) {
                Pane pane = (Pane) contentArea;
                if (pane.lookup("#" + OVERLAY_ID) != null) return;
                StackPane overlay = createOverlay();
                overlay.setId(OVERLAY_ID);
                // ensure overlay fills the pane
                overlay.prefWidthProperty().bind(pane.widthProperty());
                overlay.prefHeightProperty().bind(pane.heightProperty());
                pane.getChildren().add(overlay);
                return;
            }

            // If contentArea exists but isn't a Pane we can add to, give up to avoid full-window overlay
            return;
        };

        if (Platform.isFxApplicationThread()) showAction.run(); else Platform.runLater(showAction);
    }

    public static void hide(Node anyNode) {
        if (anyNode == null) return;
        // If a show was requested before the node was in scene, mark it canceled
        if (pendingShows.remove(anyNode)) {
            canceledShows.add(anyNode);
            return;
        }

        Runnable hideAction = () -> {
            Scene s = anyNode.getScene();
            if (s == null || s.getRoot() == null) return;
            Node contentArea = s.getRoot().lookup("#contentArea");
            // remove overlay if attached to the content area (works for any Pane)
            if (contentArea instanceof Pane) {
                Pane pane = (Pane) contentArea;
                Node existing = pane.lookup("#" + OVERLAY_ID);
                if (existing != null) pane.getChildren().remove(existing);
                return;
            }
            // fallback: remove overlay if attached to the scene root and it's a Pane
            if (s.getRoot() instanceof Pane) {
                Pane rootPane = (Pane) s.getRoot();
                Node existing = rootPane.lookup("#" + OVERLAY_ID);
                if (existing != null) rootPane.getChildren().remove(existing);
                return;
            }

            // Nothing more to do — overlays are attached to Panes only and already removed above if present
            return;
        };

        if (Platform.isFxApplicationThread()) hideAction.run(); else Platform.runLater(hideAction);
    }

    private static StackPane createOverlay() {
        StackPane root = new StackPane();
        root.setPickOnBounds(true);
        // visual appearance controlled by CSS (light/dark themes)
        root.getStyleClass().add("loading-overlay");

        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);

        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(56, 56);
        pi.getStyleClass().add("loading-indicator");

        Label lbl = new Label("Učitavanje...");
        lbl.getStyleClass().add("loading-label");

        box.getChildren().addAll(pi, lbl);
        root.getChildren().add(box);
        StackPane.setAlignment(box, Pos.CENTER);
        return root;
    }
}
