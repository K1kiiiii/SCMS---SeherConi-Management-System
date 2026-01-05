package com.scms.service;

import com.scms.config.DatabaseConfig;
import com.scms.dao.MaterialDao;
import com.scms.model.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AssignmentService {

    private final MaterialDao materialDao = new MaterialDao();

    // Assign material to a user: reduce stock and create assignment record
    public void assignMaterial(int userId, int materialId, double quantity) {
        if (userId <= 0) throw new ServiceException("user.invalid");
        if (materialId <= 0) throw new ServiceException("material.invalid");
        if (quantity <= 0) throw new ServiceException("quantity.invalid");

        try {
            // Ensure material exists
            Material m = materialDao.findById(materialId).orElseThrow(() -> new ServiceException("material.not_found"));
            if (m.getQuantity() < quantity) throw new ServiceException("material.insufficient");

            // Adjust stock
            boolean adjusted = materialDao.adjustQuantity(materialId, -quantity);
            if (!adjusted) throw new ServiceException("Failed adjusting material quantity");

            // Insert into assignments table
            String sql = "INSERT INTO assignments (user_id, material_id, quantity) VALUES (?, ?, ?)";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, materialId);
                ps.setDouble(3, quantity);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new ServiceException("Failed assigning material", ex);
        }
    }
}

