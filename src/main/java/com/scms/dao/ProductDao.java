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
        String sql = "INSERT INTO products (recipe_id, name, quantity_boxes, price_per_box, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (p.getRecipeId() == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, p.getRecipeId());
            ps.setString(2, p.getName());
            ps.setDouble(3, p.getQuantityBoxes());
            if (p.getPricePerBox() == null) ps.setNull(4, java.sql.Types.DECIMAL); else ps.setDouble(4, p.getPricePerBox());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) p.setId(keys.getInt(1)); }
        }
        return p;
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
        String sql = "UPDATE products SET recipe_id = ?, name = ?, quantity_boxes = ?, price_per_box = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (p.getRecipeId() == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, p.getRecipeId());
            ps.setString(2, p.getName());
            ps.setDouble(3, p.getQuantityBoxes());
            if (p.getPricePerBox() == null) ps.setNull(4, java.sql.Types.DECIMAL); else ps.setDouble(4, p.getPricePerBox());
            ps.setInt(5, p.getId());
            int updated = ps.executeUpdate();
            if (updated > 0) return findById(p.getId());
        }
        return Optional.empty();
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
                "name VARCHAR(255) NOT NULL, " +
                "quantity_boxes DECIMAL(12,3) NOT NULL DEFAULT 0, " +
                "price_per_box DECIMAL(12,4) NULL, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX (recipe_id)" +
                ")";
        try (Connection conn = DatabaseConfig.getConnection(); Statement st = conn.createStatement()) {
            st.execute(create);
        }
    }
}
