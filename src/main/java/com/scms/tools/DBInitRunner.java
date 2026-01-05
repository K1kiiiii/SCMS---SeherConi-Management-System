package com.scms.tools;

import com.scms.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DBInitRunner {
    public static void main(String[] args) {
        System.out.println("Running DatabaseConfig.initDatabase()...");
        DatabaseConfig.initDatabase();

        // Try to connect to server and list databases
        String host = "localhost";
        int port = 3306;
        String user = System.getenv("SCMS_DB_USER");
        if (user == null) user = "root";
        String pass = System.getenv("SCMS_DB_PASS");
        if (pass == null) pass = "root123";
        String url = "jdbc:mysql://" + host + ":" + port + "/?serverTimezone=UTC&allowPublicKeyRetrieval=true";

        System.out.println("Connecting to server to list databases...");
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW DATABASES")) {
            System.out.println("Databases:");
            while (rs.next()) {
                System.out.println(" - " + rs.getString(1));
            }
        } catch (Exception ex) {
            System.err.println("Failed listing databases: " + ex.getClass().getName() + " - " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}

