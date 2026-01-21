package com.scms.model;

import java.time.LocalDateTime;

public class Assignment {
    private int id;
    private int userId;
    private int materialId;
    private double quantity;
    private LocalDateTime assignedAt;
    private String status; // e.g. PENDING, CONFIRMED
    private String notes;

    public Assignment() {}

    public Assignment(int id, int userId, int materialId, double quantity, LocalDateTime assignedAt) {
        this.id = id;
        this.userId = userId;
        this.materialId = materialId;
        this.quantity = quantity;
        this.assignedAt = assignedAt;
    }

    public Assignment(int id, int userId, int materialId, double quantity, LocalDateTime assignedAt, String status, String notes) {
        this.id = id;
        this.userId = userId;
        this.materialId = materialId;
        this.quantity = quantity;
        this.assignedAt = assignedAt;
        this.status = status;
        this.notes = notes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getMaterialId() { return materialId; }
    public void setMaterialId(int materialId) { this.materialId = materialId; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
