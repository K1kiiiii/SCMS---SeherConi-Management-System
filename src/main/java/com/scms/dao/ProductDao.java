package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDao {

    public Product create(Product p) throws SQLException {
        ensureTableExists();
        String sqlFull = "INSERT INTO products (recipe_id, task_id, name, quantity_boxes, price_per_box, updated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        String sqlFallback = "INSERT INTO products (recipe_id, name, quantity_boxes, price_per_box, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlFull, Statement.RETURN_GENERATED_KEYS)) {
                if (p.getRecipeId() == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, p.getRecipeId());
                if (p.getTaskId() == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, p.getTaskId());
                ps.setString(3, p.getName());
                ps.setDouble(4, p.getQuantityBoxes());
                if (p.getPricePerBox() == null) ps.setNull(5, java.sql.Types.DECIMAL); else ps.setDouble(5, p.getPricePerBox());
                int affected = ps.executeUpdate();
                if (affected <= 0) throw new SQLException("No rows affected when inserting product");
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys != null && keys.next()) {
                        p.setId(keys.getInt(1));
                        return p;
                    }
                }
                // generated keys not available — try to find inserted row by unique fields
                Integer foundId = findInsertedProductId(conn, p);
                if (foundId != null) { p.setId(foundId); return p; }
                // if we reach here, insertion succeeded but we couldn't retrieve id — return p without id
                return p;
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                System.err.println("ProductDao.create - failed to insert using full SQL: " + ex.getMessage());
                ex.printStackTrace(System.err);
                if (msg.contains("task_id") || msg.contains("unknown column") || msg.contains("column not found")) {
                    // fallback to legacy insert without task_id
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlFallback, Statement.RETURN_GENERATED_KEYS)) {
                        if (p.getRecipeId() == null) ps2.setNull(1, java.sql.Types.INTEGER); else ps2.setInt(1, p.getRecipeId());
                        ps2.setString(2, p.getName());
                        ps2.setDouble(3, p.getQuantityBoxes());
                        if (p.getPricePerBox() == null) ps2.setNull(4, java.sql.Types.DECIMAL); else ps2.setDouble(4, p.getPricePerBox());
                        int affected = ps2.executeUpdate();
                        if (affected <= 0) throw new SQLException("No rows affected when inserting product (fallback)");
                        try (ResultSet keys = ps2.getGeneratedKeys()) { if (keys != null && keys.next()) { p.setId(keys.getInt(1)); return p; } }
                        Integer foundId = findInsertedProductId(conn, p);
                        if (foundId != null) { p.setId(foundId); return p; }
                        return p;
                    } catch (SQLException ex2) {
                        System.err.println("ProductDao.create - fallback insert also failed: " + ex2.getMessage());
                        ex2.printStackTrace(System.err);
                        throw ex2;
                    }
                }
                throw ex;
            }
        }
    }

    // Attempt to locate the recently inserted product by matching on (name, quantity_boxes, recipe_id/task_id) and ordering by timestamp
    private Integer findInsertedProductId(Connection conn, Product p) {
        String selWithTask = "SELECT id FROM products WHERE name = ? AND quantity_boxes = ? AND task_id = ? ORDER BY updated_at DESC LIMIT 1";
        String selNoTask = "SELECT id FROM products WHERE name = ? AND quantity_boxes = ? ORDER BY updated_at DESC LIMIT 1";
        String selByName = "SELECT id FROM products WHERE name = ? ORDER BY updated_at DESC LIMIT 1";
        try {
            if (p.getTaskId() != null) {
                try (PreparedStatement ps = conn.prepareStatement(selWithTask)) {
                    ps.setString(1, p.getName());
                    ps.setDouble(2, p.getQuantityBoxes());
                    ps.setInt(3, p.getTaskId());
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(selNoTask)) {
                ps.setString(1, p.getName());
                ps.setDouble(2, p.getQuantityBoxes());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
            }
            // fallback: match by name only (useful when quantity rounding or task_id not present)
            try (PreparedStatement ps = conn.prepareStatement(selByName)) {
                ps.setString(1, p.getName());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
            }
        } catch (SQLException ex) {
            System.err.println("ProductDao.findInsertedProductId failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
        return null;
    }

    public Optional<Product> findById(int id) throws SQLException {
        ensureTableExists();
        String sql = "SELECT * FROM products WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(map(rs)); }
        }
        return Optional.empty();
    }

    public List<Product> findAll() throws SQLException {
        ensureTableExists();
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products";
        try (Connection conn = DatabaseConfig.getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Optional<Product> update(Product p) throws SQLException {
        ensureTableExists();
        String sqlFull = "UPDATE products SET recipe_id = ?, task_id = ?, name = ?, quantity_boxes = ?, price_per_box = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        String sqlFallback = "UPDATE products SET recipe_id = ?, name = ?, quantity_boxes = ?, price_per_box = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlFull)) {
                if (p.getRecipeId() == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, p.getRecipeId());
                if (p.getTaskId() == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, p.getTaskId());
                ps.setString(3, p.getName());
                ps.setDouble(4, p.getQuantityBoxes());
                if (p.getPricePerBox() == null) ps.setNull(5, java.sql.Types.DECIMAL); else ps.setDouble(5, p.getPricePerBox());
                ps.setInt(6, p.getId());
                int updated = ps.executeUpdate();
                if (updated > 0) return findById(p.getId());
                return Optional.empty();
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                System.err.println("ProductDao.update - failed using full SQL: " + ex.getMessage());
                ex.printStackTrace(System.err);
                if (msg.contains("task_id") || msg.contains("unknown column") || msg.contains("column not found")) {
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlFallback)) {
                        if (p.getRecipeId() == null) ps2.setNull(1, java.sql.Types.INTEGER); else ps2.setInt(1, p.getRecipeId());
                        ps2.setString(2, p.getName());
                        ps2.setDouble(3, p.getQuantityBoxes());
                        if (p.getPricePerBox() == null) ps2.setNull(4, java.sql.Types.DECIMAL); else ps2.setDouble(4, p.getPricePerBox());
                        ps2.setInt(5, p.getId());
                        int updated = ps2.executeUpdate();
                        if (updated > 0) return findById(p.getId());
                        return Optional.empty();
                    } catch (SQLException ex2) {
                        System.err.println("ProductDao.update - fallback failed: " + ex2.getMessage());
                        ex2.printStackTrace(System.err);
                        throw ex2;
                    }
                }
                throw ex;
            }
        }
    }

    public boolean delete(int id) throws SQLException {
        ensureTableExists();
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id); return ps.executeUpdate() > 0;
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        int rid = rs.getInt("recipe_id"); if (rs.wasNull()) p.setRecipeId(null); else p.setRecipeId(rid);
        // task_id may be absent; handle gracefully
        try { int tid = rs.getInt("task_id"); if (rs.wasNull()) p.setTaskId(null); else p.setTaskId(tid); } catch (SQLException ignored) {}
        p.setName(rs.getString("name"));
        p.setQuantityBoxes(rs.getDouble("quantity_boxes"));
        try { double pr = rs.getDouble("price_per_box"); if (!rs.wasNull()) p.setPricePerBox(pr); } catch (SQLException ignored) {}
        Timestamp t = rs.getTimestamp("updated_at"); if (t != null) p.setUpdatedAt(t.toLocalDateTime());
        return p;
    }

    // Create the products table if it doesn't exist (runtime fallback when migration wasn't run)
    private void ensureTableExists() throws SQLException {
        String create = "CREATE TABLE IF NOT EXISTS products (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "recipe_id INT NULL, " +
                "task_id INT NULL, " +
                "name VARCHAR(255) NOT NULL, " +
                "quantity_boxes DECIMAL(12,3) NOT NULL DEFAULT 0, " +
                "price_per_box DECIMAL(12,4) NULL, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX (recipe_id), INDEX (task_id)" +
                ")";
        try (Connection conn = DatabaseConfig.getConnection(); Statement st = conn.createStatement()) {
            st.execute(create);
        }
    }
}
