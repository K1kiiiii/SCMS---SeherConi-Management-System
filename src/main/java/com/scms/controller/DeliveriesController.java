package com.scms.controller;

import com.scms.dao.InventoryMovementDao;
import com.scms.dao.ProductDao;
import com.scms.dao.MaterialDao;
import com.scms.model.InventoryMovement;
import com.scms.model.Product;
import com.scms.model.Material;
import com.scms.util.DialogUtils;
import com.scms.util.LoadingOverlay;
import com.scms.util.RoleManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DeliveriesController {

    @FXML private TableView<InventoryMovement> deliveriesTable;
    @FXML private TableColumn<InventoryMovement, Integer> colId;
    @FXML private TableColumn<InventoryMovement, String> colDate;
    @FXML private TableColumn<InventoryMovement, String> colItem;
    @FXML private TableColumn<InventoryMovement, Double> colQty;
    @FXML private TableColumn<InventoryMovement, String> colUnit;
    @FXML private TableColumn<InventoryMovement, Double> colUnitPrice;
    @FXML private TableColumn<InventoryMovement, Double> colTotalPrice;
    @FXML private TableColumn<InventoryMovement, String> colUser;

    private final InventoryMovementDao imDao = new InventoryMovementDao();
    private final ProductDao productDao = new ProductDao();
    private final MaterialDao materialDao = new MaterialDao();
    private ObservableList<InventoryMovement> items = FXCollections.observableArrayList();

    // caches for names to avoid DB calls during cell rendering
    private final Map<Integer, String> productNameMap = new HashMap<>();
    private final Map<Integer, String> materialNameMap = new HashMap<>();

    private final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        setupColumns();
        loadDeliveriesAsync();
    }

    private void setupColumns() {
        colId.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getId()));
        colDate.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getCreatedAt() == null ? "" : DATE_TIME_FORMATTER.format(cd.getValue().getCreatedAt())));
        // item: show product name if available, otherwise material name or '-'
        colItem.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(resolveItemName(cd.getValue())));
        colQty.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getQuantity()));
        colUnit.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getQuantity() == null ? "" : "boxes"));
        colUnitPrice.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getUnitPrice()));
        colTotalPrice.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getTotalPrice()));
        colUser.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getUserId() == null ? "" : String.valueOf(cd.getValue().getUserId())));
    }

    private String resolveItemName(InventoryMovement mv) {
        if (mv == null) return "";
        Integer pid = mv.getProductId();
        if (pid != null) {
            String n = productNameMap.get(pid);
            if (n != null) return n;
            // fallback to id if not cached
            return "Prod#" + pid;
        }
        Integer mid = mv.getMaterialId();
        if (mid != null) {
            String n = materialNameMap.get(mid);
            if (n != null) return n;
            return "Mat#" + mid;
        }
        return "-";
    }

    private void loadCaches() {
        try {
            List<Product> ps = productDao.findAll();
            productNameMap.clear();
            for (Product p : ps) productNameMap.put(p.getId(), p.getName());
        } catch (Exception ex) {
            // ignore; cache remains empty
        }
        try {
            List<Material> ms = materialDao.findAll();
            materialNameMap.clear();
            for (Material m : ms) materialNameMap.put(m.getId(), m.getName());
        } catch (Exception ex) {
            // ignore
        }
    }

    private void loadDeliveriesAsync() {
        // show overlay while loading deliveries
        LoadingOverlay.show(deliveriesTable);
        javafx.concurrent.Task<List<InventoryMovement>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<InventoryMovement> call() throws Exception {
                // fetch movements (here items list may be loaded later)
                return imDao.findAll();
            }
        };
        task.setOnSucceeded(ev -> {
            // populate name caches (best-effort)
            loadCaches();
            List<InventoryMovement> list = task.getValue();
            items.setAll(list);
            deliveriesTable.setItems(items);
            LoadingOverlay.hide(deliveriesTable);
        });
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            System.err.println("Failed loading deliveries async: " + (ex == null ? "unknown" : ex.toString()));
            if (ex != null) ex.printStackTrace();
            LoadingOverlay.hide(deliveriesTable);
        });
        Thread th = new Thread(task, "deliveries-loader"); th.setDaemon(true); th.start();
    }

    @FXML
    private void handleRefresh() { loadDeliveriesAsync(); }

    @FXML
    private void handleNewDelivery() {
        if (!(RoleManager.isAdmin() || RoleManager.isMagacioner())) {
            Alert a = new Alert(Alert.AlertType.WARNING); DialogUtils.styleAlert(a);
            a.setTitle("Pristup odbijen"); a.setHeaderText(null); a.setContentText("Nemate dozvolu za unos dostave."); a.showAndWait();
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Novi izvoz");
        dialog.setHeaderText("Zabilježite izvoz robe");
        ButtonType save = new ButtonType("Snimi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20,150,10,10));
        ComboBox<Product> cbProducts = new ComboBox<>();
        try { List<Product> ps = productDao.findAll(); cbProducts.setItems(FXCollections.observableArrayList(ps)); } catch (Exception ignored) {}
        cbProducts.setCellFactory(lv -> new javafx.scene.control.ListCell<>() { @Override protected void updateItem(Product i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:i.getName()); } });
        cbProducts.setButtonCell(new javafx.scene.control.ListCell<>() { @Override protected void updateItem(Product i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:i.getName()); } });

        TextField qtyField = new TextField(); qtyField.setPromptText("Broj kutija");
        TextField unitPriceField = new TextField(); unitPriceField.setPromptText("Cijena po kutiji (ostavi prazno za trenutnu)");

        grid.add(new Label("Proizvod:"), 0, 0); grid.add(cbProducts, 1, 0);
        grid.add(new Label("Broj kutija:"), 0, 1); grid.add(qtyField, 1, 1);
        grid.add(new Label("Cijena po kutiji:"), 0, 2); grid.add(unitPriceField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == save) {
                try {
                    Product p = cbProducts.getValue();
                    if (p == null) throw new IllegalArgumentException("Odaberite proizvod");
                    double qty = Double.parseDouble(qtyField.getText());
                    Double up = null;
                    if (unitPriceField.getText() != null && !unitPriceField.getText().isBlank()) up = Double.parseDouble(unitPriceField.getText());
                    if (qty <= 0) throw new IllegalArgumentException("Količina mora biti > 0");

                    InventoryMovement mv = new InventoryMovement();
                    mv.setType("OUT");
                    mv.setProductId(p.getId()); // optional link to Product (proizvod)
                    mv.setMaterialId(null);
                    mv.setQuantity(qty);
                    mv.setUnitPrice(up == null ? p.getPricePerBox() : up);
                    mv.setTotalPrice((mv.getUnitPrice() == null ? 0.0 : mv.getUnitPrice()) * qty);
                    try { mv.setUserId(RoleManager.getLoggedInUser().getId()); } catch (Exception ignored) {}
                    mv.setCreatedAt(java.time.LocalDateTime.now());

                    // insert movement
                    try { imDao.insert(mv); } catch (Exception ex) { System.err.println("Failed inserting delivery: " + ex.getMessage()); }

                    // reduce product quantity (by boxes)
                    try {
                        p.setQuantityBoxes(p.getQuantityBoxes() - qty);
                        if (up != null) p.setPricePerBox(up);
                        productDao.update(p);
                    } catch (Exception ex) { System.err.println("Failed updating product quantity: " + ex.getMessage()); }

                    return true;
                } catch (Exception ex) {
                    Alert a = new Alert(Alert.AlertType.WARNING); DialogUtils.styleAlert(a);
                    a.setTitle("Neispravan unos"); a.setHeaderText(null); a.setContentText(ex.getMessage()); a.showAndWait();
                    return false;
                }
            }
            return false;
        });

        DialogUtils.styleDialog(dialog);
        Optional<Boolean> res = dialog.showAndWait();
        if (res.isPresent() && res.get()) {
            Alert info = new Alert(Alert.AlertType.INFORMATION); DialogUtils.styleAlert(info);
            info.setTitle("Izvoz zabilježen"); info.setHeaderText(null); info.setContentText("Izvoz uspješno zabilježen."); info.showAndWait();
            loadDeliveriesAsync();
        }
    }
}
