package com.scms.config;

import com.scms.util.PasswordUtil;

import java.sql.*;

public class DatabaseConfig {

    private static final String BASE_URL = "jdbc:mysql://avnadmin:AVNS_nutl3nT8fn4JNvY39Bv@scms-db-scms.g.aivencloud.com:19009/defaultdb?ssl-mode=REQUIRED";
    private static final String USER = "avnadmin";
    private static final String PASS = "AVNS_nutl3nT8fn4JNvY39Bv";

    public static void initDatabase() {
        // By default do NOT modify schema unless explicitly enabled via env var SCMS_RUN_MIGRATIONS=true
        String runMigrationsEnv = System.getenv("SCMS_RUN_MIGRATIONS");
        boolean runMigrations = "true".equalsIgnoreCase(runMigrationsEnv);
        if (!runMigrations) {
            // Quick connectivity check only - do not create or alter schema
            try (Connection conn = DriverManager.getConnection(BASE_URL, USER, PASS)) {
                System.out.println("Database connection OK (migrations skipped). URL=" + conn.getMetaData().getURL());
            } catch (SQLException ex) {
                System.err.println("Database connectivity failed (migrations skipped): " + ex.getMessage());
                ex.printStackTrace();
            }
            return;
        }

        try {
            // Connect to the same database the application uses (BASE_URL) so we don't create/switch to a different DB
            try (Connection conn = DriverManager.getConnection(BASE_URL, USER, PASS);
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

                // materials table — include minimum_quantity and last_purchase_price so DAO updates match schema
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS materials (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(150) NOT NULL,
                      quantity DOUBLE DEFAULT 0,
                      unit VARCHAR(30),
                      supplier VARCHAR(100),
                      minimum_quantity DOUBLE NOT NULL DEFAULT 0,
                      last_purchase_price DECIMAL(12,4) NULL,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB;
                    """);

                // assignments table — include processed_by / processed_at for audit
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS assignments (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      user_id INT,
                      material_id INT,
                      quantity DOUBLE,
                      assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      status VARCHAR(50) DEFAULT 'CONFIRMED',
                      notes TEXT,
                      processed_by INT NULL,
                      processed_at TIMESTAMP NULL,
                      FOREIGN KEY (user_id) REFERENCES users(id),
                      FOREIGN KEY (material_id) REFERENCES materials(id),
                      FOREIGN KEY (processed_by) REFERENCES users(id) ON DELETE SET NULL
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

                // tasks table for production tasks
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tasks (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      recipe_id INT NOT NULL,
                      assigned_to INT,
                      created_by INT,
                      quantity_target DOUBLE,
                      unit VARCHAR(30),
                      deadline DATE,
                      status VARCHAR(50) DEFAULT 'PENDING',
                      produced_quantity DOUBLE,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      started_at TIMESTAMP NULL,
                      completed_at TIMESTAMP NULL,
                      FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                      FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL,
                      FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
                    ) ENGINE=InnoDB;
                    """);

                // After ensuring tables exist, attempt to ALTER existing tables to add missing columns
                // so DAO UPDATE statements don't fail when schema is older.
                try {
                    // materials: add minimum_quantity, last_purchase_price
                    st.executeUpdate("ALTER TABLE materials ADD COLUMN IF NOT EXISTS minimum_quantity DOUBLE NOT NULL DEFAULT 0");
                } catch (SQLException ex) {
                    try {
                        st.executeUpdate("ALTER TABLE materials ADD COLUMN minimum_quantity DOUBLE NOT NULL DEFAULT 0");
                    } catch (SQLException ex2) {
                        System.err.println("materials.minimum_quantity already present or failed to add: " + ex2.getMessage());
                    }
                }

                try {
                    st.executeUpdate("ALTER TABLE materials ADD COLUMN IF NOT EXISTS last_purchase_price DECIMAL(12,4) NULL");
                } catch (SQLException ex) {
                    try {
                        st.executeUpdate("ALTER TABLE materials ADD COLUMN last_purchase_price DECIMAL(12,4) NULL");
                    } catch (SQLException ex2) {
                        System.err.println("materials.last_purchase_price already present or failed to add: " + ex2.getMessage());
                    }
                }

                try {
                    st.executeUpdate("ALTER TABLE assignments ADD COLUMN IF NOT EXISTS processed_by INT NULL");
                } catch (SQLException ex) {
                    try { st.executeUpdate("ALTER TABLE assignments ADD COLUMN processed_by INT NULL"); } catch (SQLException ex2) { System.err.println("assignments.processed_by already present or failed to add: " + ex2.getMessage()); }
                }

                try {
                    st.executeUpdate("ALTER TABLE assignments ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP NULL");
                } catch (SQLException ex) {
                    try { st.executeUpdate("ALTER TABLE assignments ADD COLUMN processed_at TIMESTAMP NULL"); } catch (SQLException ex2) { System.err.println("assignments.processed_at already present or failed to add: " + ex2.getMessage()); }
                }

                // Try adding foreign key for processed_by if not present (best-effort)
                try {
                    st.executeUpdate("ALTER TABLE assignments ADD CONSTRAINT fk_assignments_processed_by FOREIGN KEY (processed_by) REFERENCES users(id) ON DELETE SET NULL");
                } catch (SQLException ex) {
                    System.err.println("Warning: could not add FK fk_assignments_processed_by (maybe exists): " + ex.getMessage());
                }

                // products: add task_id column if missing
                try {
                    st.executeUpdate("ALTER TABLE products ADD COLUMN IF NOT EXISTS task_id INT NULL");
                } catch (SQLException ex) {
                    try { st.executeUpdate("ALTER TABLE products ADD COLUMN task_id INT NULL"); } catch (SQLException ex2) { System.err.println("products.task_id already present or failed to add: " + ex2.getMessage()); }
                }
                try {
                    st.executeUpdate("ALTER TABLE products ADD INDEX (task_id)");
                } catch (SQLException ex) {
                    // ignore if index exists
                }

                // Insert default roles if not present
                st.executeUpdate("INSERT IGNORE INTO roles (name, description) VALUES ('admin','Administrator'),('worker','Worker'),('storekeeper','Storekeeper');");

                // Insert default admin user if not exists
                String checkAdminSql = "SELECT COUNT(*) FROM users WHERE username='admin'";
                try (ResultSet rs = st.executeQuery(checkAdminSql)) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        String hashed = PasswordUtil.hashPassword("admin");
                        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)") ) {
                            ps.setString(1, "admin");
                            ps.setString(2, hashed);
                            ps.setString(3, "admin");
                            ps.executeUpdate();
                        }
                    }
                }
            }

        } catch (SQLException ex) {
            System.err.println("Failed initializing database: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // glavna veza sa bazom podataka - restored to original BASE_URL so old data is visible
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(BASE_URL, USER, PASS);
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
