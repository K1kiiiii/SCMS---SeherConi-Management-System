package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.Material;
import com.scms.util.ResultSetMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MaterialDao {

    public Material create(Material m) throws SQLException {
        // include minimum_quantity and last_purchase_price when creating new material
        String sqlFull = "INSERT INTO materials (name, quantity, unit, supplier, minimum_quantity, last_purchase_price) VALUES (?, ?, ?, ?, ?, ?)";
        String sqlFallback = "INSERT INTO materials (name, quantity, unit, supplier) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlFull, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, m.getName());
                ps.setDouble(2, m.getQuantity());
                ps.setString(3, m.getUnit());
                ps.setString(4, m.getSupplier());
                ps.setDouble(5, m.getMinimumQuantity());
                if (m.getLastPurchasePrice() == null) ps.setNull(6, java.sql.Types.DECIMAL); else ps.setDouble(6, m.getLastPurchasePrice());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) m.setId(keys.getInt(1)); }
                return m;
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (msg.contains("minimum_quantity") || msg.contains("last_purchase_price") || msg.contains("unknown column") || msg.contains("column not found")) {
                    // fallback to legacy insert without optional columns
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlFallback, Statement.RETURN_GENERATED_KEYS)) {
                        ps2.setString(1, m.getName());
                        ps2.setDouble(2, m.getQuantity());
                        ps2.setString(3, m.getUnit());
                        ps2.setString(4, m.getSupplier());
                        ps2.executeUpdate();
                        try (ResultSet keys = ps2.getGeneratedKeys()) { if (keys.next()) m.setId(keys.getInt(1)); }
                        return m;
                    }
                }
                throw ex;
            }
        }
    }

    public Optional<Material> findById(int id) throws SQLException {
        String sql = "SELECT * FROM materials WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(ResultSetMapper.mapMaterial(rs)); }
        }
        return Optional.empty();
    }

    public Optional<Material> findByName(String name) throws SQLException {
        String sql = "SELECT * FROM materials WHERE name = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(ResultSetMapper.mapMaterial(rs)); }
        }
        return Optional.empty();
    }

    public List<Material> findAll() throws SQLException {
        List<Material> list = new ArrayList<>();
        // select minimum_quantity as well
        String sql = "SELECT * FROM materials";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(ResultSetMapper.mapMaterial(rs));
        }
        return list;
    }

    public Optional<Material> update(Material m) throws SQLException {
        String sqlFull = "UPDATE materials SET name = ?, quantity = ?, unit = ?, supplier = ?, minimum_quantity = ?, last_purchase_price = ? WHERE id = ?";
        String sqlFallback = "UPDATE materials SET name = ?, quantity = ?, unit = ?, supplier = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlFull)) {
                ps.setString(1, m.getName());
                ps.setDouble(2, m.getQuantity());
                ps.setString(3, m.getUnit());
                ps.setString(4, m.getSupplier());
                ps.setDouble(5, m.getMinimumQuantity());
                if (m.getLastPurchasePrice() == null) ps.setNull(6, java.sql.Types.DECIMAL); else ps.setDouble(6, m.getLastPurchasePrice());
                ps.setInt(7, m.getId());
                int updated = ps.executeUpdate();
                if (updated > 0) return findById(m.getId());
                return Optional.empty();
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (msg.contains("minimum_quantity") || msg.contains("last_purchase_price") || msg.contains("unknown column") || msg.contains("column not found")) {
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlFallback)) {
                        ps2.setString(1, m.getName());
                        ps2.setDouble(2, m.getQuantity());
                        ps2.setString(3, m.getUnit());
                        ps2.setString(4, m.getSupplier());
                        ps2.setInt(5, m.getId());
                        int updated = ps2.executeUpdate();
                        if (updated > 0) return findById(m.getId());
                        return Optional.empty();
                    }
                }
                throw ex;
            }
        }
    }

    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM materials WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean adjustQuantity(int id, double delta) throws SQLException {
        String sql = "UPDATE materials SET quantity = quantity + ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    // Return list of materials where current quantity is below the material's minimum
    public List<Material> findMaterialsBelowMinimum() throws SQLException {
        List<Material> list = new ArrayList<>();
        String sql = "SELECT * FROM materials WHERE quantity < minimum_quantity";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(ResultSetMapper.mapMaterial(rs));
        }
        return list;
    }
}
