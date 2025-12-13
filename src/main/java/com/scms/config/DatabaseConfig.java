package com.scms.config;

import com.scms.util.PasswordUtil;

import java.sql.*;

public class DatabaseConfig {
    private static final String HOST = "localhost";
    private static final int PORT = 3306;
    private static final String DB_NAME = "scms_db";

    // Allow overriding DB credentials via environment variables to avoid committing secrets
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "Auth_*#13wow"; // fallback; prefer env var
    private static final String USER;
    private static final String PASS;

    static {
        String envUser = System.getenv("SCMS_DB_USER");
        String envPass = System.getenv("SCMS_DB_PASS");
        USER = (envUser != null && !envUser.isBlank()) ? envUser : DEFAULT_USER;
        PASS = (envPass != null) ? envPass : DEFAULT_PASS;
    }

    private static final String BASE_URL = "jdbc:mysql://" + HOST + ":" + PORT + "/?serverTimezone=UTC&allowPublicKeyRetrieval=true";

    public static void initDatabase() {
        try {
            // 1) Connect to server (no DB) to create DB if not exists
            try (Connection conn = DriverManager.getConnection(BASE_URL, USER, PASS);
                 Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;");
            }

            // 2) Connect to the created DB to create tables
            String dbUrl = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME + "?serverTimezone=UTC&allowPublicKeyRetrieval=true";
            try (Connection conn = DriverManager.getConnection(dbUrl, USER, PASS);
                 Statement st = conn.createStatement()) {

                // users table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      username VARCHAR(100) NOT NULL UNIQUE,
                      password_hash VARCHAR(128) NOT NULL,
                      role VARCHAR(50) NOT NULL,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB;
                    """);

                // roles table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS roles (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(50) NOT NULL UNIQUE,
                      description VARCHAR(255)
                    ) ENGINE=InnoDB;
                    """);

                // placeholder tables (materials, assignments)
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS materials (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(150) NOT NULL,
                      quantity DOUBLE DEFAULT 0,
                      unit VARCHAR(30),
                      supplier VARCHAR(100),
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB;
                    """);

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS assignments (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      user_id INT,
                      material_id INT,
                      quantity DOUBLE,
                      assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      status VARCHAR(50) DEFAULT 'CONFIRMED',
                      notes TEXT,
                      FOREIGN KEY (user_id) REFERENCES users(id),
                      FOREIGN KEY (material_id) REFERENCES materials(id)
                    ) ENGINE=InnoDB;
                    """);

                // recipes and recipe_items
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recipes (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(150) NOT NULL,
                      description TEXT,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB;
                    """);

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recipe_items (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      recipe_id INT NOT NULL,
                      material_id INT NOT NULL,
                      quantity DOUBLE,
                      unit VARCHAR(30),
                      FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                      FOREIGN KEY (material_id) REFERENCES materials(id)
                    ) ENGINE=InnoDB;
                    """);

                // Insert default roles if not present
                st.executeUpdate("INSERT IGNORE INTO roles (name, description) VALUES ('admin','Administrator'),('worker','Worker'),('storekeeper','Storekeeper');");

                // Insert default admin user if not exists
                String checkAdminSql = "SELECT COUNT(*) FROM users WHERE username='admin'";
                try (ResultSet rs = st.executeQuery(checkAdminSql)) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        String hashed = PasswordUtil.hashPassword("admin");
                        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)")) {
                            ps.setString(1, "admin");
                            ps.setString(2, hashed);
                            ps.setString(3, "admin");
                            ps.executeUpdate();
                        }
                    }
                }
            }

            System.out.println("Database initialization completed (db=" + DB_NAME + ").");
        } catch (SQLException ex) {
            System.err.println("Failed initializing database: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // glavna veza sa bazom podataka
    public static Connection getConnection() throws SQLException {
        String dbUrl = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME + "?serverTimezone=UTC&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(dbUrl, USER, PASS);
    }

    // Helper for quick debugging from command line
    public static void testConnection() {
        try (Connection c = getConnection()) {
            System.out.println("DB URL: " + c.getMetaData().getURL());
            System.out.println("DB Product: " + c.getMetaData().getDatabaseProductName() + " v" + c.getMetaData().getDatabaseProductVersion());
        } catch (SQLException ex) {
            System.err.println("DB test failed: " + ex.getClass().getName() + " - " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
