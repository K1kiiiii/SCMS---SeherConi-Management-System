package com.scms.model;

import java.time.LocalDateTime;

public class Material {
    private int id;
    private String name;
    // current quantity in stock
    private double quantity;
    // minimum allowed quantity for this material (from DB)
    private double minimumQuantity;
    private String unit;
    private String supplier;
    private LocalDateTime updatedAt;

    public Material() {}

    public Material(int id, String name, double quantity, String unit, String supplier, LocalDateTime updatedAt) {
        this.id = id; this.name = name; this.quantity = quantity; this.unit = unit; this.supplier = supplier; this.updatedAt = updatedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    // new getter/setter for minimum quantity
    public double getMinimumQuantity() { return minimumQuantity; }
    public void setMinimumQuantity(double minimumQuantity) { this.minimumQuantity = minimumQuantity; }

    // convenience alias to emphasize current quantity
    public double getCurrentQuantity() { return quantity; }
    public void setCurrentQuantity(double currentQuantity) { this.quantity = currentQuantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
