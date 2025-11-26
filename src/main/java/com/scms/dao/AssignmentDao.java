package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.Assignment;
import com.scms.util.ResultSetMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AssignmentDao {

    public Assignment create(Assignment a) throws SQLException {
        String selectQtySql = "SELECT quantity FROM materials WHERE id = ? FOR UPDATE";
        String updateQtySql = "UPDATE materials SET quantity = quantity - ? WHERE id = ?";
        String insertSql = "INSERT INTO assignments (user_id, material_id, quantity) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock and check available quantity
                try (PreparedStatement psSelect = conn.prepareStatement(selectQtySql)) {
                    psSelect.setInt(1, a.getMaterialId());
                    try (ResultSet rs = psSelect.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Material not found for id=" + a.getMaterialId());
                        double available = rs.getDouble(1);
                        if (available < a.getQuantity()) {
                            throw new SQLException("Insufficient material quantity. Available=" + available + ", requested=" + a.getQuantity());
                        }
                    }
                }

                // Subtract assigned quantity
                try (PreparedStatement psUpdate = conn.prepareStatement(updateQtySql)) {
                    psUpdate.setDouble(1, a.getQuantity());
                    psUpdate.setInt(2, a.getMaterialId());
                    int updated = psUpdate.executeUpdate();
                    if (updated <= 0) throw new SQLException("Failed to update material quantity for id=" + a.getMaterialId());
                }

                // Now insert the assignment
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, a.getUserId());
                    ps.setInt(2, a.getMaterialId());
                    ps.setDouble(3, a.getQuantity());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) a.setId(keys.getInt(1)); }
                }

                conn.commit();
                return a;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public Optional<Assignment> findById(int id) throws SQLException {
        String sql = "SELECT * FROM assignments WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(ResultSetMapper.mapAssignment(rs)); }
        }
        return Optional.empty();
    }

    public List<Assignment> findByUserId(int userId) throws SQLException {
        List<Assignment> list = new ArrayList<>();
        String sql = "SELECT * FROM assignments WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(ResultSetMapper.mapAssignment(rs)); }
        }
        return list;
    }

    public List<Assignment> findAll() throws SQLException {
        List<Assignment> list = new ArrayList<>();
        String sql = "SELECT * FROM assignments";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(ResultSetMapper.mapAssignment(rs));
        }
        return list;
    }

    public Optional<Assignment> update(Assignment a) throws SQLException {
        String sql = "UPDATE assignments SET user_id = ?, material_id = ?, quantity = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a.getUserId());
            ps.setInt(2, a.getMaterialId());
            ps.setDouble(3, a.getQuantity());
            ps.setInt(4, a.getId());
            int updated = ps.executeUpdate();
            if (updated > 0) return findById(a.getId());
        }
        return Optional.empty();
    }

    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM assignments WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
