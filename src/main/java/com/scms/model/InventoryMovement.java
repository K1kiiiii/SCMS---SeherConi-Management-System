package com.scms.model;

import java.time.LocalDateTime;

public class InventoryMovement {
    private Integer id;
    private Integer materialId; // nullable
    private Integer productId; // was recipeId; now connected to Product (proizvod)
    private String type; // "IN" or "OUT"
    private Double quantity;
    private Double unitPrice; // nullable
    private Double totalPrice; // nullable
    private Integer userId; // who recorded the movement
    private Integer relatedAssignmentId;
    private Integer relatedTaskId;
    private String notes;
    private LocalDateTime createdAt;

    public InventoryMovement() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Integer materialId) {
        this.materialId = materialId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getRelatedAssignmentId() {
        return relatedAssignmentId;
    }

    public void setRelatedAssignmentId(Integer relatedAssignmentId) {
        this.relatedAssignmentId = relatedAssignmentId;
    }

    public Integer getRelatedTaskId() {
        return relatedTaskId;
    }

    public void setRelatedTaskId(Integer relatedTaskId) {
        this.relatedTaskId = relatedTaskId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
