package com.scms.controller;

import com.scms.dao.InventoryMovementDao;
import com.scms.dao.ProductDao;
import com.scms.model.InventoryMovement;
import com.scms.model.Product;
import com.scms.util.DialogUtils;
import com.scms.util.RoleManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;

import java.time.LocalDateTime;
import java.util.List;
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
    private ObservableList<InventoryMovement> items = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        loadDeliveries();
    }

    private void loadDeliveries() {
        try {
            deliveriesTable.setItems(items);
        } catch (Exception ex) {
            System.err.println("Failed loading deliveries: " + ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() { loadDeliveries(); }

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
                    mv.setRecipeId(p.getRecipeId()); // optional link
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
            loadDeliveries();
        }
    }
}
