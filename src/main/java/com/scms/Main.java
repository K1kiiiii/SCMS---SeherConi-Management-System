package com.scms;

import com.scms.config.DatabaseConfig;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // initialize local DB & default data
        DatabaseConfig.initDatabase();

        Parent root = FXMLLoader.load(getClass().getResource("/com/scms/view/login.fxml"));
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/scms/css/dark-theme.css").toExternalForm());
        primaryStage.setTitle("SCMS - Login");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
