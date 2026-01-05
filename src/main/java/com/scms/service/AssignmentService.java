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
            throw new ServiceException("Greška pri kreiranju zahtjeva", ex);
        }
    }

    // kreiranje zahtjeva
    public Assignment requestMaterial(int userId, int materialId, double qty, String notes) {
        if (userId <= 0) throw new ServiceException("user.invalid");
        if (materialId <= 0) throw new ServiceException("material.invalid");
        if (qty <= 0) throw new ServiceException("quantity.invalid");

        try {
            Assignment a = new Assignment();
            a.setUserId(userId);
            a.setMaterialId(materialId);
            a.setQuantity(qty);
            a.setStatus("PENDING");
            a.setNotes(notes);
            return dao.createRequest(a);
        } catch (SQLException ex) {
            throw new ServiceException("Greška pri kreiranju zahtjeva", ex);
        }
    }

    public Assignment getAssignment(int id) {
        try {
            return dao.findById(id).orElseThrow(() -> new ServiceException("assignment.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Greška pri dobavljanju zahtjeva", ex);
        }
    }

    public List<Assignment> listAssignmentsForUser(int userId) {
        try {
            return dao.findByUserId(userId);
        } catch (SQLException ex) {
            throw new ServiceException("Greška pri dobavljanju zahtjeva", ex);
        }
    }

    public List<Assignment> listAll() {
        try {
            return dao.findAll();
        } catch (SQLException ex) {
            throw new ServiceException("Greška pri dobavljanju zahtjeva", ex);
        }
    }

    public Assignment approveRequest(int assignmentId) {
        try {
            return dao.approveRequest(assignmentId).orElseThrow(() -> new ServiceException("assignment.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Greška pri odobravanju zahtjeva", ex);
        }
    }

    public Assignment rejectRequest(int assignmentId, String reason) {
        try {
            return dao.rejectRequest(assignmentId, reason).orElseThrow(() -> new ServiceException("assignment.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Greška pri odbijanju zahtjeva", ex);

        }
    }

    public void removeAssignment(int id) {
        try {
            if (!dao.delete(id)) throw new ServiceException("assignment.not_found");
        } catch (SQLException ex) {
            throw new ServiceException("Greška pri brisanju zahtjeva", ex);
        }
    }
}
