package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.InventoryMovement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InventoryMovementDao {

    public InventoryMovementDao() {}

    public InventoryMovement insert(InventoryMovement mv) throws Exception {
        String sql = "INSERT INTO inventory_movements (material_id, product_id, type, quantity, unit_price, total_price, user_id, related_assignment_id, related_task_id, notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (mv.getMaterialId() == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, mv.getMaterialId());
            if (mv.getProductId() == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, mv.getProductId());
            ps.setString(3, mv.getType());
            ps.setDouble(4, mv.getQuantity() == null ? 0.0 : mv.getQuantity());
            if (mv.getUnitPrice() == null) ps.setNull(5, java.sql.Types.DECIMAL); else ps.setDouble(5, mv.getUnitPrice());
            if (mv.getTotalPrice() == null) ps.setNull(6, java.sql.Types.DECIMAL); else ps.setDouble(6, mv.getTotalPrice());
            if (mv.getUserId() == null) ps.setNull(7, java.sql.Types.INTEGER); else ps.setInt(7, mv.getUserId());
            if (mv.getRelatedAssignmentId() == null) ps.setNull(8, java.sql.Types.INTEGER); else ps.setInt(8, mv.getRelatedAssignmentId());
            if (mv.getRelatedTaskId() == null) ps.setNull(9, java.sql.Types.INTEGER); else ps.setInt(9, mv.getRelatedTaskId());
            if (mv.getNotes() == null) ps.setNull(10, java.sql.Types.VARCHAR); else ps.setString(10, mv.getNotes());
            if (mv.getCreatedAt() == null) ps.setTimestamp(11, java.sql.Timestamp.valueOf(LocalDateTime.now())); else ps.setTimestamp(11, java.sql.Timestamp.valueOf(mv.getCreatedAt()));

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) mv.setId(keys.getInt(1));
            }
            return mv;
        }
    }

    public List<InventoryMovement> findByMaterialBetweenDates(int materialId, java.time.LocalDateTime from, java.time.LocalDateTime to) throws Exception {
        String sql = "SELECT * FROM inventory_movements WHERE material_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at";
        List<InventoryMovement> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, materialId);
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(from));
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InventoryMovement mv = map(rs);
                    list.add(mv);
                }
            }
        } catch (java.sql.SQLException ex) {
            // If the table does not exist, attempt to create it and retry once
            String sqlState = ex.getSQLState();
            if ("42S02".equals(sqlState) || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("doesn't exist"))) {
                System.err.println("inventory_movements table not found (findByMaterialBetweenDates) - attempting to create it and retry");
                DatabaseConfig.ensureInventoryMovementsTableExists();
                // retry once
                try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, materialId);
                    ps.setTimestamp(2, java.sql.Timestamp.valueOf(from));
                    ps.setTimestamp(3, java.sql.Timestamp.valueOf(to));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            InventoryMovement mv = map(rs);
                            list.add(mv);
                        }
                    }
                    return list;
                } catch (java.sql.SQLException ex2) {
                    System.err.println("Retry after creating inventory_movements failed: " + ex2.getMessage());
                    return list;
                }
            }
            throw ex;
        }
        return list;
    }

    public List<InventoryMovement> findAll() throws Exception {
        String sql = "SELECT * FROM inventory_movements ORDER BY created_at DESC";
        List<InventoryMovement> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (java.sql.SQLException ex) {
            String sqlState = ex.getSQLState();
            if ("42S02".equals(sqlState) || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("doesn't exist"))) {
                System.err.println("inventory_movements table not found (findAll) - attempting to create it and retry");
                DatabaseConfig.ensureInventoryMovementsTableExists();
                // retry once
                try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(map(rs));
                    return list;
                } catch (java.sql.SQLException ex2) {
                    System.err.println("Retry after creating inventory_movements failed: " + ex2.getMessage());
                    return list;
                }
            }
            throw ex;
        }
        return list;
    }

    private InventoryMovement map(ResultSet rs) throws Exception {
        InventoryMovement mv = new InventoryMovement();
        mv.setId(rs.getInt("id"));
        int mid = rs.getInt("material_id"); if (rs.wasNull()) mv.setMaterialId(null); else mv.setMaterialId(mid);
        int pid = rs.getInt("product_id"); if (rs.wasNull()) mv.setProductId(null); else mv.setProductId(pid);
        mv.setType(rs.getString("type"));
        mv.setQuantity(rs.getDouble("quantity"));
        double up = rs.getDouble("unit_price"); if (rs.wasNull()) mv.setUnitPrice(null); else mv.setUnitPrice(up);
        double tp = rs.getDouble("total_price"); if (rs.wasNull()) mv.setTotalPrice(null); else mv.setTotalPrice(tp);
        int uid = rs.getInt("user_id"); if (rs.wasNull()) mv.setUserId(null); else mv.setUserId(uid);
        int aid = rs.getInt("related_assignment_id"); if (rs.wasNull()) mv.setRelatedAssignmentId(null); else mv.setRelatedAssignmentId(aid);
        int tid = rs.getInt("related_task_id"); if (rs.wasNull()) mv.setRelatedTaskId(null); else mv.setRelatedTaskId(tid);
        mv.setNotes(rs.getString("notes"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at"); if (ts != null) mv.setCreatedAt(ts.toLocalDateTime());
        return mv;
    }
}
