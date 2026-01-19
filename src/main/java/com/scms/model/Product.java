package com.scms.model;

import java.time.LocalDateTime;

public class Product {
    private int id;
    private Integer recipeId; // link to recipe
    private String name;
    // quantity stored in boxes
    private double quantityBoxes;
    // price per box
    private Double pricePerBox;
    private LocalDateTime updatedAt;

    public Product() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getRecipeId() { return recipeId; }
    public void setRecipeId(Integer recipeId) { this.recipeId = recipeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getQuantityBoxes() { return quantityBoxes; }
    public void setQuantityBoxes(double quantityBoxes) { this.quantityBoxes = quantityBoxes; }

    public Double getPricePerBox() { return pricePerBox; }
    public void setPricePerBox(Double pricePerBox) { this.pricePerBox = pricePerBox; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

