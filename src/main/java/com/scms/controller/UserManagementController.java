package com.scms.controller;

import com.scms.model.User;
import com.scms.service.ServiceException;
import com.scms.service.UserService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

public class UserManagementController {
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colRole;

    @FXML private TextField searchField;
    @FXML private Button addButton;
    @FXML private Button deleteButton;
    @FXML private Button editButton;

    private final UserService userService = new UserService();
    private FilteredList<User> filteredUsers;
    private ObservableList<User> allUsers;

    @FXML
    public void initialize() {
        setupColumns();
        loadUsers();
        setupSearch();

    }
    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
    }
    private void loadUsers() {
        try {
            List<User> fromDb = userService.getAllUsers();
            allUsers = FXCollections.observableArrayList(fromDb);
            filteredUsers = new FilteredList<>(allUsers, u -> true);
            userTable.setItems(filteredUsers);
        } catch (ServiceException ex) {
            showError("Greška pri učitavanju korisnika", ex.getMessage());
        }
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String text = newValue == null ? "" : newValue.toLowerCase();

            if (filteredUsers == null) return;

            filteredUsers.setPredicate(u -> {
                if (u == null) return false;

                return u.getUsername().toLowerCase().contains(text)
                        || (u.getRole() != null
                        && u.getRole().toLowerCase().contains(text));
            });
        });
    }
    @FXML
    private void handleAddUser(ActionEvent event) {
        UserDialogResult newUser = showCreateUserDialog();
        if (newUser != null) {
            try {
                User created = userService.createUser(
                        newUser.username,
                        newUser.plainPassword,
                        newUser.role
                );

                if (created != null) {
                    allUsers.add(created);
                } else {
                    // fallback: ponovo učitaj sve
                    loadUsers();
                }
            } catch (ServiceException ex) {
                showError("Greška pri kreiranju korisnika", ex.getMessage());
            }
        }
    }

    @FXML
    private void handleEditUser(ActionEvent event) {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Nije odabran korisnik",
                    "Molimo odaberite korisnika kojeg želite urediti.");
            return;
        }
        UserDialogResult data = showEditUserDialog(selected);
        if (data == null) {
            return;
        }

            try {
                selected.setUsername(data.username);
                selected.setRole(data.role);
                userService.updateUser(selected);
                userTable.refresh();
            } catch (ServiceException ex) {
                showError("Greška pri ažuriranju korisnika", ex.getMessage());
            }
        }

    @FXML
    private void handleDeleteUser(ActionEvent event) {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Nije odabran korisnik",
                    "Molimo odaberite korisnika kojeg želite obrisati.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Brisanje korisnika");
        confirm.setHeaderText("Da li ste sigurni da želite obrisati odabranog korisnika?");
        confirm.setContentText(selected.getUsername() + " (" + selected.getRole() + ")");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                userService.deleteUser(selected.getId());
                allUsers.remove(selected);
            } catch (ServiceException ex) {
                showError("Greška pri brisanju korisnika", ex.getMessage());
            }
        }
    }

    private static class UserDialogResult {
        String username;
        String plainPassword; // samo za create; kod edit može biti null
        String role;

        UserDialogResult(String username, String plainPassword, String role) {
            this.username = username;
            this.plainPassword = plainPassword;
            this.role = role;
        }
    }
    private UserDialogResult showCreateUserDialog() {
        Dialog<UserDialogResult> dialog = new Dialog<>();
        dialog.setTitle("Novi korisnik");
        dialog.setHeaderText("Unesite podatke o korisniku");

        ButtonType saveButtonType = new ButtonType("Snimi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        ComboBox<String> roleCombo = new ComboBox<>();

        roleCombo.getItems().addAll("admin", "magacioner", "radnik");
        roleCombo.setValue("radnik");

        grid.add(new Label("Korisničko ime:"), 0, 0);
        grid.add(usernameField, 1, 0);

        grid.add(new Label("Lozinka:"), 0, 1);
        grid.add(passwordField, 1, 1);

        grid.add(new Label("Uloga:"), 0, 2);
        grid.add(roleCombo, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {

                String username = usernameField.getText();
                String password = passwordField.getText();
                String role     = roleCombo.getValue();

                if (username == null || username.isBlank()) {
                    showWarning("Neispravan unos", "Korisničko ime je obavezno.");
                    return null;
                }
                if (password == null || password.length() < 4) {
                    showWarning("Neispravan unos", "Lozinka mora imati najmanje 4 znaka.");
                    return null;
                }
                if (role == null || role.isBlank()) {
                    showWarning("Neispravan unos", "Uloga je obavezna.");
                    return null;
                }

                return new UserDialogResult(username, password, role);
            }
            return null;
        });

        Optional<UserDialogResult> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private UserDialogResult showEditUserDialog(User user) {
        Dialog<UserDialogResult> dialog = new Dialog<>();
        dialog.setTitle("Uredi korisnika");
        dialog.setHeaderText("Uredi postojeći korisnički nalog");

        ButtonType saveButtonType = new ButtonType("Snimi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField(user.getUsername());
        ComboBox<String> roleCombo = new ComboBox<>();

        roleCombo.getItems().addAll("admin", "magacioner", "radnik");
        roleCombo.setValue(user.getRole());

        grid.add(new Label("Korisničko ime:"), 0, 0);
        grid.add(usernameField, 1, 0);

        grid.add(new Label("Uloga:"), 0, 1);
        grid.add(roleCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {

                String username = usernameField.getText();
                String role     = roleCombo.getValue();

                if (username == null || username.isBlank()) {
                    showWarning("Neispravan unos", "Korisničko ime je obavezno.");
                    return null;
                }
                if (role == null || role.isBlank()) {
                    showWarning("Neispravan unos", "Uloga je obavezna.");
                    return null;
                }

                // lozinku ne diramo u edit modu
                return new UserDialogResult(username, null, role);
            }
            return null;
        });

        Optional<UserDialogResult> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


