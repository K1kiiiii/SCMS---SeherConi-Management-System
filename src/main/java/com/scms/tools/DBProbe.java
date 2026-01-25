package com.scms.tools;

import com.scms.config.DatabaseConfig;

import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBProbe {
    private static final Logger LOGGER = Logger.getLogger(DBProbe.class.getName());
    public static void main(String[] args) {
        LOGGER.info("Attempting DB connection using DatabaseConfig...");
        try (Connection conn = DatabaseConfig.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                LOGGER.info("Connected to DB successfully: " + conn.getMetaData().getURL());
            } else {
                LOGGER.warning("Connection is null or closed");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "DB connection failed: " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            System.exit(2);
        }
    }
}
