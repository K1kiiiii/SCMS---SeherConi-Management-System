package com.scms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Simple Task model representing a production task assigned to a worker.
 * Fields are kept explicit and have clear names to match database columns.
 */
public class Task {
    private int id;
    private int recipeId;
    private Integer assignedTo; // user id of worker
    private Integer createdBy; // admin who created the task
    private double quantityTarget;
    private String unit;
    private LocalDate deadline;
    private String status; // PENDING, IN_PROGRESS, COMPLETED
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Double producedQuantity;

    public Task() {}

    // getters / setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRecipeId() { return recipeId; }
    public void setRecipeId(int recipeId) { this.recipeId = recipeId; }

    public Integer getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Integer assignedTo) { this.assignedTo = assignedTo; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public double getQuantityTarget() { return quantityTarget; }
    public void setQuantityTarget(double quantityTarget) { this.quantityTarget = quantityTarget; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Double getProducedQuantity() { return producedQuantity; }
    public void setProducedQuantity(Double producedQuantity) { this.producedQuantity = producedQuantity; }
}

