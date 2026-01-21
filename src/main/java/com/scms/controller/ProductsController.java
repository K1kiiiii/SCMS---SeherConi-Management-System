package com.scms.controller;

import com.scms.dao.ProductDao;
import com.scms.dao.RecipeDao;
import com.scms.dao.TaskDao;
import com.scms.model.Product;
import com.scms.model.Recipe;
import com.scms.model.Task;
import com.scms.util.DialogUtils;
import com.scms.util.LoadingOverlay;
import com.scms.util.RoleManager;
import com.scms.util.InputSanitizer;
import com.scms.util.TableUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProductsController {

    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, Double> colQty;
    @FXML private TableColumn<Product, Double> colPrice;
    @FXML private TableColumn<Product, Integer> colRecipe;
    @FXML private TableColumn<Product, Integer> colTask;

    private final ProductDao productDao = new ProductDao();
    private final RecipeDao recipeDao = new RecipeDao();
    private final TaskDao taskDao = new TaskDao();

    private final ObservableList<Product> items = FXCollections.observableArrayList();

    // caches to show names instead of ids
    private final Map<Integer, String> recipeNameMap = new HashMap<>();
    private final Map<Integer, String> taskDisplayMap = new HashMap<>();

    @FXML
    public void initialize() {
        // Initialize table columns bindings (simple property accessors)
        try {
            colId.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getId()));
            colName.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getName()));
            colQty.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getQuantityBoxes()));
            colPrice.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getPricePerBox()));

            // recipe column: show recipe name when available
            colRecipe.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getRecipeId()));
            colRecipe.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                        setText(null);
                    } else {
                        Product p = (Product) getTableRow().getItem();
                        Integer rid = p.getRecipeId();
                        if (rid == null) setText("");
                        else setText(recipeNameMap.getOrDefault(rid, "#" + rid));
                    }
                }
            });

            // task column: show task display string
            if (colTask != null) {
                colTask.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getTaskId()));
                colTask.setCellFactory(column -> new TableCell<>() {
                    @Override
                    protected void updateItem(Integer item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                            setText(null);
                        } else {
                            Product p = (Product) getTableRow().getItem();
                            Integer tid = p.getTaskId();
                            if (tid == null) setText("");
                            else setText(taskDisplayMap.getOrDefault(tid, "#" + tid));
                        }
                    }
                });
            }
        } catch (Exception ignore) { }

        // Load products in background so FXML loading doesn't block when DB is slow/unavailable
        loadProductsAsync();
    }

    private void loadProducts() {
        // synchronous load used by other parts if needed (keeps behavior)
        try {
            List<Product> list = productDao.findAll();
            // refresh caches
            refreshRecipeAndTaskCaches();
            items.setAll(list);
            productsTable.setItems(items);
            // auto size columns
            TableUtils.autoResizeColumnsToFitContent(productsTable);
        } catch (Exception ex) {
            System.err.println("Failed loading products: " + ex.getMessage());
        }
    }

    private void refreshRecipeAndTaskCaches() {
        try {
            recipeNameMap.clear();
            List<Recipe> recs = recipeDao.findAll();
            for (Recipe r : recs) recipeNameMap.put(r.getId(), r.getName());
        } catch (Exception ignored) {}
        try {
            taskDisplayMap.clear();
            List<Task> tasks = taskDao.findAll();
            for (Task t : tasks) {
                String disp = "#" + t.getId() + (t.getRecipeId() > 0 ? " - Recept " + t.getRecipeId() : "");
                taskDisplayMap.put(t.getId(), disp);
            }
        } catch (Exception ignored) {}
    }

    private void loadProductsAsync() {
        // show overlay on the products table (will attach to contentArea when available)
        LoadingOverlay.show(productsTable);
        javafx.concurrent.Task<List<Product>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                // populate caches first
                refreshRecipeAndTaskCaches();
                return productDao.findAll();
            }
        };
        task.setOnSucceeded(ev -> {
            List<Product> list = task.getValue();
            items.setAll(list);
            productsTable.setItems(items);
            // auto-size columns
            TableUtils.autoResizeColumnsToFitContent(productsTable);
            LoadingOverlay.hide(productsTable);
        });
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            System.err.println("Failed loading products async: " + (ex == null ? "unknown" : ex.toString()));
            if (ex != null) ex.printStackTrace();
            LoadingOverlay.hide(productsTable);
        });
        Thread t = new Thread(task, "products-loader");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleRefresh() { loadProducts(); }

    @FXML
    private void handleNewProduct() {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Novi proizvod");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.setPadding(new Insets(10));
        TextField nameField = new TextField(); nameField.setPromptText("Naziv proizvoda");
        TextField qtyField = new TextField(); qtyField.setPromptText("Količina (kutije)");
        TextField priceField = new TextField(); priceField.setPromptText("Cijena po kutiji");

        ComboBox<Recipe> cbRecipes = new ComboBox<>();
        try { List<Recipe> recs = recipeDao.findAll(); cbRecipes.setItems(FXCollections.observableArrayList(recs)); } catch (Exception ignored) {}
        cbRecipes.setCellFactory(lv -> new javafx.scene.control.ListCell<>(){ @Override protected void updateItem(Recipe i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:i.getName()); }});
        cbRecipes.setButtonCell(new javafx.scene.control.ListCell<>(){ @Override protected void updateItem(Recipe i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:i.getName()); }});

        ComboBox<Task> cbTasks = new ComboBox<>();
        try { List<Task> tasks = taskDao.findAll(); cbTasks.setItems(FXCollections.observableArrayList(tasks)); } catch (Exception ignored) {}
        cbTasks.setCellFactory(lv -> new javafx.scene.control.ListCell<>(){ @Override protected void updateItem(Task i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:("#"+i.getId()+" - " + (i.getRecipeId()>0?"Recept "+i.getRecipeId():""))); }});
        cbTasks.setButtonCell(new javafx.scene.control.ListCell<>(){ @Override protected void updateItem(Task i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:("#"+i.getId()+" - " + (i.getRecipeId()>0?"Recept "+i.getRecipeId():""))); }});

        grid.add(new Label("Naziv:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Recept (opcionalno):"), 0, 1); grid.add(cbRecipes, 1, 1);
        grid.add(new Label("Povezan zadatak (opcionalno):"), 0, 2); grid.add(cbTasks, 1, 2);
        grid.add(new Label("Količina (kutije):"), 0, 3); grid.add(qtyField, 1, 3);
        grid.add(new Label("Cijena po kutiji:"), 0, 4); grid.add(priceField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Product p = new Product();
                    p.setName(InputSanitizer.sanitizeText(nameField.getText()));
                    if (cbRecipes.getValue() != null) p.setRecipeId(cbRecipes.getValue().getId());
                    if (cbTasks.getValue() != null) p.setTaskId(cbTasks.getValue().getId());
                    Double qty = InputSanitizer.parseDoubleOrNull(qtyField.getText());
                    Double price = InputSanitizer.parseDoubleOrNull(priceField.getText());
                    if (qty == null || price == null) throw new IllegalArgumentException("Provjerite unesene vrijednosti.");
                    p.setQuantityBoxes(qty);
                    p.setPricePerBox(price);
                    return p;
                } catch (Exception ex) {
                    Alert a = new Alert(Alert.AlertType.WARNING); DialogUtils.styleAlert(a);
                    a.setTitle("Neispravan unos"); a.setHeaderText(null); a.setContentText("Provjerite unesene vrijednosti."); a.showAndWait();
                    return null;
                }
            }
            return null;
        });

        DialogUtils.styleDialog(dialog);
        Optional<Product> res = dialog.showAndWait();
        if (res.isPresent()) {
            try { productDao.create(res.get()); loadProducts(); } catch (Exception ex) { System.err.println("Failed creating product: " + ex.getMessage()); }
        }
    }

    @FXML
    private void handleEditProduct() {
        Product sel = productsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!(RoleManager.isAdmin() || RoleManager.isMagacioner())) {
            Alert a = new Alert(Alert.AlertType.WARNING); DialogUtils.styleAlert(a);
            a.setTitle("Pristup odbijen"); a.setHeaderText(null); a.setContentText("Nemate dozvolu za uređivanje cijene."); a.showAndWait();
            return;
        }

        Dialog<Product> dialog = new Dialog<>(); dialog.setTitle("Uredi proizvod"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.setPadding(new Insets(10));
        TextField nameField = new TextField(sel.getName()); TextField qtyField = new TextField(String.valueOf(sel.getQuantityBoxes())); TextField priceField = new TextField(sel.getPricePerBox()==null?"":String.valueOf(sel.getPricePerBox()));

        ComboBox<Task> cbTasks = new ComboBox<>();
        try { List<Task> tasks = taskDao.findAll(); cbTasks.setItems(FXCollections.observableArrayList(tasks)); } catch (Exception ignored) {}
        cbTasks.setCellFactory(lv -> new javafx.scene.control.ListCell<>(){ @Override protected void updateItem(Task i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:("#"+i.getId()+" - " + (i.getRecipeId()>0?"Recept "+i.getRecipeId():""))); }});
        cbTasks.setButtonCell(new javafx.scene.control.ListCell<>(){ @Override protected void updateItem(Task i, boolean empty) { super.updateItem(i, empty); setText(empty||i==null?null:("#"+i.getId()+" - " + (i.getRecipeId()>0?"Recept "+i.getRecipeId():""))); }});
        // preselect current
        if (sel.getTaskId() != null) {
            try { taskDao.findById(sel.getTaskId()).ifPresent(t -> cbTasks.getSelectionModel().select(t)); } catch (Exception ignored) {}
        }

        grid.add(new Label("Naziv:"),0,0); grid.add(nameField,1,0);
        grid.add(new Label("Povezan zadatak (opcionalno):"),0,1); grid.add(cbTasks,1,1);
        grid.add(new Label("Količina (kutije):"),0,2); grid.add(qtyField,1,2);
        grid.add(new Label("Cijena po kutiji:"),0,3); grid.add(priceField,1,3);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> { if (btn==ButtonType.OK) { try {
            sel.setName(InputSanitizer.sanitizeText(nameField.getText()));
            Double qty = InputSanitizer.parseDoubleOrNull(qtyField.getText());
            Double price = InputSanitizer.parseDoubleOrNull(priceField.getText());
            if (qty==null || price==null) return null;
            sel.setQuantityBoxes(qty);
            sel.setPricePerBox(price);
            sel.setTaskId(cbTasks.getValue()==null?null:cbTasks.getValue().getId());
            return sel; } catch (Exception ex) { return null; } } return null; });
        DialogUtils.styleDialog(dialog);
        Optional<Product> res = dialog.showAndWait(); if (res.isPresent()) { try { productDao.update(res.get()); loadProducts(); } catch (Exception ex) { System.err.println("Failed updating product: " + ex.getMessage()); } }
    }

    @FXML
    private void handleDeleteProduct() {
        Product sel = productsTable.getSelectionModel().getSelectedItem(); if (sel==null) return;
        Alert c = new Alert(Alert.AlertType.CONFIRMATION); DialogUtils.styleAlert(c); c.setTitle("Brisanje"); c.setHeaderText(null); c.setContentText("Sigurno obrisati proizvod?"); Optional<ButtonType> r = c.showAndWait(); if (r.isPresent() && r.get()==ButtonType.OK) { try { productDao.delete(sel.getId()); loadProducts(); } catch (Exception ex) { System.err.println("Failed deleting product: " + ex.getMessage()); } }
    }
}
