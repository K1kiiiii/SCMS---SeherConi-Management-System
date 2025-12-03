package com.scms.tools;

import com.scms.config.DatabaseConfig;

import java.sql.Connection;

public class DBProbe {
    public static void main(String[] args) {
        System.out.println("Attempting DB connection using DatabaseConfig...");
        try (Connection conn = DatabaseConfig.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("Connected to DB successfully: " + conn.getMetaData().getURL());
            } else {
                System.out.println("Connection is null or closed");
            }
        } catch (Exception ex) {
            System.err.println("DB connection failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }
    }
}

