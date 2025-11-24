package com.scms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class MainController {
    @FXML
    private Label welcomeLabel;

    @FXML
    public void initialize() {
        welcomeLabel.setText("Welcome to SCMS — Dashboard (placeholder)");
    }
}
