package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.Task;
import com.scms.util.ResultSetMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minimal DAO for Task entity. Keeps SQL simple and mirrors existing DAO patterns.
 */
public class TaskDao {

    public Task create(Task t) throws SQLException {
        String sql = "INSERT INTO tasks (recipe_id, assigned_to, created_by, quantity_target, unit, deadline, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getRecipeId());
            if (t.getAssignedTo() != null) ps.setInt(2, t.getAssignedTo()); else ps.setNull(2, Types.INTEGER);
            if (t.getCreatedBy() != null) ps.setInt(3, t.getCreatedBy()); else ps.setNull(3, Types.INTEGER);
            ps.setDouble(4, t.getQuantityTarget());
            ps.setString(5, t.getUnit());
            if (t.getDeadline() != null) ps.setDate(6, Date.valueOf(t.getDeadline())); else ps.setNull(6, Types.DATE);
            ps.setString(7, t.getStatus() != null ? t.getStatus() : "PENDING");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) t.setId(keys.getInt(1)); }

            // fetch created_at
            String sel = "SELECT created_at FROM tasks WHERE id = ?";
            try (PreparedStatement ps2 = conn.prepareStatement(sel)) {
                ps2.setInt(1, t.getId());
                try (ResultSet rs = ps2.executeQuery()) { if (rs.next()) t.setCreatedAt(rs.getTimestamp(1).toLocalDateTime()); }
            }
        }
        return t;
    }

    public List<Task> findByAssignedUser(int userId) throws SQLException {
        List<Task> list = new ArrayList<>();
        String sql = "SELECT * FROM tasks WHERE assigned_to = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(ResultSetMapper.mapTask(rs));
            }
        }
        return list;
    }

    public boolean updateStatus(int taskId, String status) throws SQLException {
        String sql = "UPDATE tasks SET status = ?, started_at = CASE WHEN ? = 'IN_PROGRESS' THEN CURRENT_TIMESTAMP ELSE started_at END, completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ps.setString(3, status);
            ps.setInt(4, taskId);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<Task> findById(int id) throws SQLException {
        String sql = "SELECT * FROM tasks WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(ResultSetMapper.mapTask(rs)); }
        }
        return Optional.empty();
    }

    public boolean completeTask(int taskId, Double producedQty) throws SQLException {
        String sql = "UPDATE tasks SET status = 'COMPLETED', produced_quantity = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (producedQty != null) ps.setDouble(1, producedQty); else ps.setNull(1, Types.DOUBLE);
            ps.setInt(2, taskId);
            return ps.executeUpdate() > 0;
        }
    }
}
