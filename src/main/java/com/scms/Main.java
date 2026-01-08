package com.scms;

import com.scms.config.DatabaseConfig;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // initialize local DB & default data
        DatabaseConfig.initDatabase();

        String[] fonts = {
                "/com/scms/fonts/LeagueSpartan-Regular.ttf",
                "/com/scms/fonts/LeagueSpartan-Medium.ttf",
                "/com/scms/fonts/LeagueSpartan-SemiBold.ttf",
                "/com/scms/fonts/leaguespartan-bold.ttf"
        };

        for (String fontPath : fonts) {
            try (java.io.InputStream fontStream = getClass().getResourceAsStream(fontPath)) {
                if (fontStream != null) {
                    Font.loadFont(fontStream, 14);
                    System.out.println("Loaded font: " + fontPath);
                } else {
                    System.err.println("Warning: bundled font not found: " + fontPath);
                }
            } catch (Exception ex) {
                System.err.println("Failed to load bundled font " + fontPath + ": " + ex.getMessage());
            }
        }


        Parent root = FXMLLoader.load(getClass().getResource("/com/scms/view/login.fxml"));
        Scene scene = new Scene(root);

        // Respect saved theme preference for initial login scene
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(com.scms.controller.LoginController.class);
            boolean dark = prefs.getBoolean("dark_mode", false);
            String cssPath = dark ? "/com/scms/css/dark-theme.css" : "/com/scms/css/light-theme.css";
            java.net.URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception ex) {
            System.err.println("Failed to load initial stylesheet: " + ex.getMessage());
        }

        primaryStage.setTitle("SCMS - Login");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
