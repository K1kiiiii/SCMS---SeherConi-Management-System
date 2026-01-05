package com.scms.service;

import com.scms.dao.MaterialDao;
import com.scms.model.Material;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple notification service that periodically checks for materials below their minimum
 * and triggers a single notification when a material crosses from OK -> BELOW_MINIMUM.
 *
 * Implementation notes:
 * - Uses JavaFX ScheduledService to execute checks on a background thread
 * - Keeps a set of previously alerted material IDs to avoid spamming alerts
 * - Exposes start/stop methods to control lifecycle
 */
public class NotificationService {
    private final MaterialDao materialDao = new MaterialDao();
    private final ScheduledService<List<Material>> scheduledService;
    // previously alerted (below-minimum) material ids
    private final Set<Integer> alerted = new HashSet<>();

    public NotificationService() {
        scheduledService = new ScheduledService<>() {
            @Override
            protected Task<List<Material>> createTask() {
                return new Task<>() {
                    @Override
                    protected List<Material> call() throws Exception {
                        try {
                            return materialDao.findMaterialsBelowMinimum();
                        } catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                };
            }
        };
        // run every 15 seconds by default
        scheduledService.setPeriod(javafx.util.Duration.seconds(15));
        scheduledService.setOnSucceeded(evt -> {
            List<Material> below = scheduledService.getValue();
            if (below == null) return;
            Set<Integer> nowBelow = below.stream().map(Material::getId).collect(Collectors.toSet());
            // detect newly dropped materials: in nowBelow but not in alerted
            Set<Integer> newly = new HashSet<>();
            for (Integer id : nowBelow) if (!alerted.contains(id)) newly.add(id);
            if (!newly.isEmpty()) {
                // prepare message with names
                String msg = below.stream().filter(m -> newly.contains(m.getId()))
                        .map(m -> m.getName() + ": " + m.getQuantity() + " " + (m.getUnit()!=null?m.getUnit():""))
                        .collect(Collectors.joining("\n"));
                // update alerted set
                alerted.addAll(newly);
                // show alert on FX thread
                final String alertMsg = msg;
                Platform.runLater(() -> {
                    Alert a = new Alert(AlertType.WARNING);
                    a.setTitle("Upozorenje: Niske zalihe");
                    a.setHeaderText("Sirovine pale ispod minimalne zalihe");
                    a.setContentText(alertMsg);
                    a.showAndWait();
                });
            }
            // remove ids that are now OK (so we can alert again if they drop later)
            Set<Integer> still = new HashSet<>(alerted);
            for (Integer id : still) if (!nowBelow.contains(id)) alerted.remove(id);
        });
    }

    public void start() {
        if (!scheduledService.isRunning()) scheduledService.restart();
    }

    public void stop() {
        if (scheduledService.isRunning()) scheduledService.cancel();
    }

    // For test/inspection
    public Set<Integer> getAlertedSet() { return new HashSet<>(alerted); }
}

