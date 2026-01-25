package com.scms.tools;

import com.scms.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBTestInsert {
    private static final Logger LOGGER = Logger.getLogger(DBTestInsert.class.getName());
    public static void main(String[] args) {
        LOGGER.info("DBTestInsert: ensuring inventory_movements table exists...");
        DatabaseConfig.ensureInventoryMovementsTableExists();

        try (Connection c = DatabaseConfig.getConnection()) {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM inventory_movements")) {
                rs.next();
                int cnt = rs.getInt(1);
                LOGGER.info("Current inventory_movements count: " + cnt);
                if (cnt == 0) {
                    LOGGER.info("Inserting a test inventory_movements row...");
                    String insertSql = "INSERT INTO inventory_movements (material_id, product_id, type, quantity, unit_price, total_price, user_id, related_assignment_id, related_task_id, notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
                    try (PreparedStatement ps = c.prepareStatement(insertSql)) {
                        ps.setNull(1, java.sql.Types.INTEGER);
                        ps.setNull(2, java.sql.Types.INTEGER);
                        ps.setString(3, "IN");
                        ps.setDouble(4, 1.0);
                        ps.setNull(5, java.sql.Types.DECIMAL);
                        ps.setNull(6, java.sql.Types.DECIMAL);
                        ps.setNull(7, java.sql.Types.INTEGER);
                        ps.setNull(8, java.sql.Types.INTEGER);
                        ps.setNull(9, java.sql.Types.INTEGER);
                        ps.setString(10, "test insert from DBTestInsert");
                        int updated = ps.executeUpdate();
                        LOGGER.info("Inserted rows: " + updated);
                    }
                }
            }

            LOGGER.info("Listing up to 10 rows from inventory_movements:");
            try (PreparedStatement ps2 = c.prepareStatement("SELECT id, material_id, product_id, type, quantity, notes, created_at FROM inventory_movements ORDER BY created_at DESC LIMIT 10"); ResultSet rs2 = ps2.executeQuery()) {
                while (rs2.next()) {
                    LOGGER.info(String.format("id=%d material_id=%s product_id=%s type=%s qty=%s notes=%s created_at=%s",
                            rs2.getInt("id"),
                            rs2.getObject("material_id") == null ? "NULL" : rs2.getObject("material_id").toString(),
                            rs2.getObject("product_id") == null ? "NULL" : rs2.getObject("product_id").toString(),
                            rs2.getString("type"),
                            rs2.getObject("quantity") == null ? "NULL" : rs2.getObject("quantity").toString(),
                            rs2.getString("notes"),
                            rs2.getTimestamp("created_at")
                    ));
                }
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "DBTestInsert failed: " + ex.getClass().getName() + " - " + ex.getMessage(), ex);
            System.exit(2);
        }

        LOGGER.info("DBTestInsert finished.");
    }
}
