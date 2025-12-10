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
        String sql = "INSERT INTO materials (name, quantity, unit, supplier) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getName());
            ps.setDouble(2, m.getQuantity());
            ps.setString(3, m.getUnit());
            ps.setString(4, m.getSupplier());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) m.setId(keys.getInt(1)); }
        }
        return m;
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
        String sql = "SELECT * FROM materials";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(ResultSetMapper.mapMaterial(rs));
        }
        return list;
    }

    public Optional<Material> update(Material m) throws SQLException {
        String sql = "UPDATE materials SET name = ?, quantity = ?, unit = ?, supplier = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getName());
            ps.setDouble(2, m.getQuantity());
            ps.setString(3, m.getUnit());
            ps.setString(4, m.getSupplier());
            ps.setInt(5, m.getId());
            int updated = ps.executeUpdate();
            if (updated > 0) return findById(m.getId());
        }
        return Optional.empty();
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

    // Transactional overload: operate using provided connection and ensure quantity doesn't go negative.
    public boolean adjustQuantity(Connection conn, int id, double delta) throws SQLException {
        String selectSql = "SELECT quantity FROM materials WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false; // material not found
                double current = rs.getDouble(1);
                double updated = current + delta;
                if (updated < 0) return false; // insufficient stock
            }
        }
        String updateSql = "UPDATE materials SET quantity = quantity + ? WHERE id = ?";
        try (PreparedStatement ps2 = conn.prepareStatement(updateSql)) {
            ps2.setDouble(1, delta);
            ps2.setInt(2, id);
            return ps2.executeUpdate() > 0;
        }
    }
}
