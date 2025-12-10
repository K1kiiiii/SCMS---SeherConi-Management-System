package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.AssignmentRequest;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AssignmentRequestDao {

    public AssignmentRequest create(AssignmentRequest r) throws SQLException {
        String insertSql = "INSERT INTO assignment_requests (user_id, material_id, quantity, notes, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getUserId());
            ps.setInt(2, r.getMaterialId());
            ps.setDouble(3, r.getQuantity());
            ps.setString(4, r.getNotes());
            ps.setString(5, r.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) r.setId(keys.getInt(1)); }
        }
        return r;
    }

    public Optional<AssignmentRequest> findById(int id) throws SQLException {
        String sql = "SELECT * FROM assignment_requests WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(mapRequest(rs)); }
        }
        return Optional.empty();
    }

    public List<AssignmentRequest> findByUserId(int userId) throws SQLException {
        List<AssignmentRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM assignment_requests WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRequest(rs)); }
        }
        return list;
    }

    public List<AssignmentRequest> findByStatus(String status) throws SQLException {
        List<AssignmentRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM assignment_requests WHERE status = ? ORDER BY requested_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRequest(rs)); }
        }
        return list;
    }

    public List<AssignmentRequest> findAll() throws SQLException {
        List<AssignmentRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM assignment_requests ORDER BY requested_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRequest(rs));
        }
        return list;
    }

    public boolean updateStatus(int id, String status) throws SQLException {
        String sql = "UPDATE assignment_requests SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    // Transactional overload: update status using provided Connection
    public boolean updateStatus(Connection conn, int id, String status) throws SQLException {
        String sql = "UPDATE assignment_requests SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    private AssignmentRequest mapRequest(ResultSet rs) throws SQLException {
        AssignmentRequest r = new AssignmentRequest();
        r.setId(rs.getInt("id"));
        r.setUserId(rs.getInt("user_id"));
        r.setMaterialId(rs.getInt("material_id"));
        r.setQuantity(rs.getDouble("quantity"));
        try { r.setNotes(rs.getString("notes")); } catch (SQLException ignored) {}
        r.setStatus(rs.getString("status"));
        Timestamp t = rs.getTimestamp("requested_at");
        if (t != null) r.setRequestedAt(t.toLocalDateTime());
        return r;
    }
}
