package com.scms.tools;

import com.scms.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBInitRunner {
    private static final Logger LOGGER = Logger.getLogger(DBInitRunner.class.getName());

    public static void main(String[] args) {
        LOGGER.info("Running DatabaseConfig.initDatabase()...");
        DatabaseConfig.initDatabase();

        // Try to connect to server and list databases
        String host = "localhost";
        int port = 3306;
        String user = System.getenv("SCMS_DB_USER");
        if (user == null) user = "root";
        String pass = System.getenv("SCMS_DB_PASS");
        if (pass == null) pass = "root123";
        String url = "jdbc:mysql://" + host + ":" + port + "/?serverTimezone=UTC&allowPublicKeyRetrieval=true";

        LOGGER.info("Connecting to server to list databases...");
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW DATABASES")) {
            LOGGER.info("Databases:");
            while (rs.next()) {
                LOGGER.info(" - " + rs.getString(1));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed listing databases: " + ex.getClass().getName() + " - " + ex.getMessage(), ex);
        }
    }
}
