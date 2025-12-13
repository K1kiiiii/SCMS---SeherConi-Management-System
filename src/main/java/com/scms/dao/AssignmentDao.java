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
                // provjera dostupnosti sirovine
                try (PreparedStatement psSelect = conn.prepareStatement(selectQtySql)) {
                    psSelect.setInt(1, a.getMaterialId());
                    try (ResultSet rs = psSelect.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Sirovina sa id=" + a.getMaterialId()+" ne postoji.");
                        double available = rs.getDouble(1);
                        if (available < a.getQuantity()) {
                            throw new SQLException("Nedovoljan broj zaliha na stanju. Dostupno=" + available + ", zatraženo=" + a.getQuantity());
                        }
                    }
                }
                // ažuriranje količine sirovina
                try (PreparedStatement psUpdate = conn.prepareStatement(updateQtySql)) {
                    psUpdate.setDouble(1, a.getQuantity());
                    psUpdate.setInt(2, a.getMaterialId());
                    int updated = psUpdate.executeUpdate();
                    if (updated <= 0) throw new SQLException("Greška pri ažuriranju sirovine sa id=" + a.getMaterialId());
                }

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

    // pending request (nece utjecat na stanje zaliha)
    public Assignment createRequest(Assignment a) throws SQLException {
        String insertSql = "INSERT INTO assignments (user_id, material_id, quantity, status, notes) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getUserId());
            ps.setInt(2, a.getMaterialId());
            ps.setDouble(3, a.getQuantity());
            ps.setString(4, a.getStatus());
            ps.setString(5, a.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) a.setId(keys.getInt(1)); }
            return a;
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

    public Optional<Assignment> approveRequest(int assignmentId) throws SQLException {
        String selectAssignmentSql = "SELECT material_id, quantity, status FROM assignments WHERE id = ? FOR UPDATE";
        String selectQtySql = "SELECT quantity FROM materials WHERE id = ? FOR UPDATE";
        String updateQtySql = "UPDATE materials SET quantity = quantity - ? WHERE id = ?";
        String updateAssignmentSql = "UPDATE assignments SET status = 'CONFIRMED', assigned_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int materialId;
                double qty;
                String status;

                try (PreparedStatement ps = conn.prepareStatement(selectAssignmentSql)) {
                    ps.setInt(1, assignmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Zahtjev sa id=" + assignmentId+" ne postoji.");
                        materialId = rs.getInt(1);
                        qty = rs.getDouble(2);
                        status = rs.getString(3);
                    }
                }

                if (status != null && !status.equalsIgnoreCase("PENDING")) {
                    throw new SQLException("Samo PENDING zahtjevi se mogu odobriti. Trenutni status=" + status);
                }

                try (PreparedStatement psSelect = conn.prepareStatement(selectQtySql)) {
                    psSelect.setInt(1, materialId);
                    try (ResultSet rs = psSelect.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Tražena sirovina sa id=" + materialId+" ne postoji.");
                        double available = rs.getDouble(1);
                        if (available < qty) throw new SQLException("Nema dovoljno sirovina na zalihi. Dostupno=" + available + ", zatraženo=" + qty);
                    }
                }

                try (PreparedStatement psUpdate = conn.prepareStatement(updateQtySql)) {
                    psUpdate.setDouble(1, qty);
                    psUpdate.setInt(2, materialId);
                    int updated = psUpdate.executeUpdate();
                    if (updated <= 0) throw new SQLException("Neuspješno ažuriranje količine sirovine sa id=" + materialId);
                }

                try (PreparedStatement ps = conn.prepareStatement(updateAssignmentSql)) {
                    ps.setInt(1, assignmentId);
                    int updated = ps.executeUpdate();
                    if (updated <= 0) throw new SQLException("Failed to update assignment status for id=" + assignmentId);
                }

                conn.commit();
                return findById(assignmentId);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public Optional<Assignment> rejectRequest(int assignmentId, String reason) throws SQLException {
        String updateSql = "UPDATE assignments SET status = 'REJECTED', notes = CONCAT(IFNULL(notes, ''), ?) WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            String appended = "\n[REJECTED] " + (reason != null ? reason : "");
            ps.setString(1, appended);
            ps.setInt(2, assignmentId);
            int updated = ps.executeUpdate();
            if (updated > 0) return findById(assignmentId);
            return Optional.empty();
        }
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
