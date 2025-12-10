package com.scms.service;

import com.scms.dao.AssignmentRequestDao;
import com.scms.dao.MaterialDao;
import com.scms.config.DatabaseConfig;
import com.scms.model.AssignmentRequest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AssignmentRequestService {
    private final AssignmentRequestDao dao = new AssignmentRequestDao();
    private final MaterialDao materialDao = new MaterialDao();

    public AssignmentRequest createRequest(int userId, int materialId, double quantity, String notes) {
        if (userId <= 0) throw new ServiceException("user.invalid");
        if (materialId <= 0) throw new ServiceException("material.invalid");
        if (quantity <= 0) throw new ServiceException("quantity.invalid");

        try {
            AssignmentRequest r = new AssignmentRequest();
            r.setUserId(userId);
            r.setMaterialId(materialId);
            r.setQuantity(quantity);
            r.setNotes(notes);
            r.setStatus("PENDING");
            return dao.create(r);
        } catch (SQLException ex) {
            throw new ServiceException("Failed creating assignment request", ex);
        }
    }

    public List<AssignmentRequest> listByStatus(String status) {
        try {
            return dao.findByStatus(status);
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching requests", ex);
        }
    }

    public List<AssignmentRequest> listByUser(int userId) {
        try {
            return dao.findByUserId(userId);
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching user requests", ex);
        }
    }

    public List<AssignmentRequest> listAll() {
        try {
            return dao.findAll();
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching all requests", ex);
        }
    }

    public void approveRequest(int requestId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            try {
                conn.setAutoCommit(false);

                AssignmentRequest req = dao.findById(requestId).orElseThrow(() -> new ServiceException("request.not_found"));
                if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                    throw new ServiceException("request.invalid_state");
                }

                // Try to decrement material quantity inside same transaction; delta is negative
                boolean adjusted = materialDao.adjustQuantity(conn, req.getMaterialId(), -req.getQuantity());
                if (!adjusted) {
                    conn.rollback();
                    throw new ServiceException("material.insufficient_or_not_found");
                }

                boolean updated = dao.updateStatus(conn, requestId, "APPROVED");
                if (!updated) {
                    conn.rollback();
                    throw new ServiceException("Failed updating request status");
                }

                conn.commit();
            } catch (SQLException | ServiceException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                if (ex instanceof ServiceException) throw (ServiceException) ex;
                throw new ServiceException("Failed approving request", ex);
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
            }
        } catch (SQLException ex) {
            throw new ServiceException("Failed approving request", ex);
        }
    }

    public void rejectRequest(int requestId) {
        try {
            AssignmentRequest req = dao.findById(requestId).orElseThrow(() -> new ServiceException("request.not_found"));
            // Only allow rejecting pending requests
            if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                throw new ServiceException("request.invalid_state");
            }
            boolean ok = dao.updateStatus(requestId, "REJECTED");
            if (!ok) throw new ServiceException("Failed updating request status");
        } catch (SQLException ex) {
            throw new ServiceException("Failed rejecting request", ex);
        }
    }
}
