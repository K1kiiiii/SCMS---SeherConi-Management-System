package com.scms.service;

import com.scms.dao.AssignmentDao;
import com.scms.model.Assignment;

import java.sql.SQLException;
import java.util.List;

public class AssignmentService {

    private final AssignmentDao dao = new AssignmentDao();

    public Assignment assignMaterial(int userId, int materialId, double qty) {
        if (userId <= 0) throw new ServiceException("user.invalid");
        if (materialId <= 0) throw new ServiceException("material.invalid");
        if (qty <= 0) throw new ServiceException("quantity.invalid");

        try {
            Assignment a = new Assignment();
            a.setUserId(userId);
            a.setMaterialId(materialId);
            a.setQuantity(qty);
            return dao.create(a);
        } catch (SQLException ex) {
            throw new ServiceException("Failed creating assignment", ex);
        }
    }

    public Assignment getAssignment(int id) {
        try {
            return dao.findById(id).orElseThrow(() -> new ServiceException("assignment.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching assignment", ex);
        }
    }

    public List<Assignment> listAssignmentsForUser(int userId) {
        try {
            return dao.findByUserId(userId);
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching assignments", ex);
        }
    }

    public void removeAssignment(int id) {
        try {
            if (!dao.delete(id)) throw new ServiceException("assignment.not_found");
        } catch (SQLException ex) {
            throw new ServiceException("Failed deleting assignment", ex);
        }
    }
}

